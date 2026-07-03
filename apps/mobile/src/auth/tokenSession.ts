import type { AccessTokenProvider } from "./tokenProvider.ts";

export interface TokenSession {
  userId: string;
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
}

export interface SecureTokenStore {
  getItem(key: string): string | null | Promise<string | null>;
  setItem(key: string, value: string): void | Promise<void>;
  deleteItem(key: string): void | Promise<void>;
}

export interface NativeSecureStoreOptions {
  namespace: string;
  encryptedAtRest: boolean;
  hardwareBacked: boolean;
  deviceOnly: boolean;
  biometricOrPasscodeRequired: boolean;
  synchronizesToCloud: boolean;
}

export interface NativeSecureStoreDriver {
  getItem(key: string, options: NativeSecureStoreOptions): string | null | Promise<string | null>;
  setItem(key: string, value: string, options: NativeSecureStoreOptions): void | Promise<void>;
  deleteItem(key: string, options: NativeSecureStoreOptions): void | Promise<void>;
}

export interface SecureStoreReadinessReport {
  ready: boolean;
  blockers: string[];
}

export interface TokenVault {
  load(): Promise<TokenSession | null>;
  save(session: TokenSession): Promise<void>;
  clear(): Promise<void>;
}

export type TokenRefreshPort = (refreshToken: string, currentSession: TokenSession) => Promise<TokenSession>;

export class TokenSessionError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "TokenSessionError";
    this.code = code;
  }
}

export interface SecureTokenVaultConfig {
  store: SecureTokenStore;
  key?: string;
}

export interface NativeSecureTokenStoreConfig {
  driver: NativeSecureStoreDriver;
  options: NativeSecureStoreOptions;
}

export class NativeSecureTokenStore implements SecureTokenStore {
  private readonly driver: NativeSecureStoreDriver;
  private readonly options: NativeSecureStoreOptions;

  constructor(config: NativeSecureTokenStoreConfig) {
    this.driver = config.driver;
    this.options = normalizeSecureStoreOptions(config.options);

    const report = new SecureStoreReadinessValidator().validate(this.options);
    if (!report.ready) {
      throw new TokenSessionError("AUTH_SECURE_STORE_UNSAFE", report.blockers.join("; "));
    }
  }

  async getItem(key: string): Promise<string | null> {
    return this.driver.getItem(key, this.options);
  }

  async setItem(key: string, value: string): Promise<void> {
    await this.driver.setItem(key, value, this.options);
  }

  async deleteItem(key: string): Promise<void> {
    await this.driver.deleteItem(key, this.options);
  }
}

export class SecureStoreReadinessValidator {
  validate(options: NativeSecureStoreOptions): SecureStoreReadinessReport {
    const normalized = normalizeSecureStoreOptions(options);
    const blockers: string[] = [];

    if (!normalized.encryptedAtRest) {
      blockers.push("native secure storage must encrypt token sessions at rest");
    }
    if (!normalized.hardwareBacked) {
      blockers.push("native secure storage must use hardware-backed keys");
    }
    if (!normalized.deviceOnly) {
      blockers.push("token sessions must stay on this device only");
    }
    if (!normalized.biometricOrPasscodeRequired) {
      blockers.push("biometric or device passcode access must protect refresh tokens");
    }
    if (normalized.synchronizesToCloud) {
      blockers.push("token sessions must not sync to cloud backups");
    }
    if (normalized.namespace === "") {
      blockers.push("secure storage namespace is required");
    }

    return {
      ready: blockers.length === 0,
      blockers,
    };
  }
}

export class SecureTokenVault implements TokenVault {
  private readonly store: SecureTokenStore;
  private readonly key: string;

  constructor(config: SecureTokenVaultConfig) {
    this.store = config.store;
    this.key = config.key ?? "voiceSecure.tokenSession";
  }

  async load(): Promise<TokenSession | null> {
    const raw = await this.store.getItem(this.key);
    if (raw == null) {
      return null;
    }

    try {
      return normalizeSession(JSON.parse(raw));
    } catch (error) {
      await this.clear();
      throw new TokenSessionError("AUTH_SESSION_CORRUPT", "stored token session is corrupt");
    }
  }

  async save(session: TokenSession): Promise<void> {
    await this.store.setItem(this.key, JSON.stringify(normalizeSession(session)));
  }

  async clear(): Promise<void> {
    await this.store.deleteItem(this.key);
  }
}

export interface TokenSessionAccessTokenProviderConfig {
  vault: TokenVault;
  refresh: TokenRefreshPort;
  clock?: () => Date;
  refreshWindowMs?: number;
}

export class TokenSessionAccessTokenProvider implements AccessTokenProvider {
  private readonly vault: TokenVault;
  private readonly refresh: TokenRefreshPort;
  private readonly clock: () => Date;
  private readonly refreshWindowMs: number;

  constructor(config: TokenSessionAccessTokenProviderConfig) {
    this.vault = config.vault;
    this.refresh = config.refresh;
    this.clock = config.clock ?? (() => new Date());
    this.refreshWindowMs = config.refreshWindowMs ?? 30_000;
  }

  async getAccessToken(): Promise<string> {
    const session = await this.vault.load();
    if (!session) {
      throw new TokenSessionError("AUTH_SESSION_REQUIRED", "mobile auth session is required");
    }

    if (!this.shouldRefresh(session)) {
      return session.accessToken;
    }

    try {
      const refreshed = normalizeSession(await this.refresh(session.refreshToken, session));
      await this.vault.save(refreshed);
      return refreshed.accessToken;
    } catch (error) {
      await this.vault.clear();
      throw new TokenSessionError("AUTH_REFRESH_FAILED", "mobile auth refresh failed");
    }
  }

  private shouldRefresh(session: TokenSession): boolean {
    const expiresAt = Date.parse(session.accessTokenExpiresAt);
    const now = this.clock().getTime();
    return expiresAt - now <= this.refreshWindowMs;
  }
}

function normalizeSession(value: unknown): TokenSession {
  if (!isRecord(value)) {
    throw new TokenSessionError("AUTH_SESSION_INVALID", "token session must be an object");
  }

  const accessTokenExpiresAt = requireNonBlank(value.accessTokenExpiresAt, "accessTokenExpiresAt");
  if (Number.isNaN(Date.parse(accessTokenExpiresAt))) {
    throw new TokenSessionError("AUTH_SESSION_INVALID", "accessTokenExpiresAt must be an ISO date");
  }

  return {
    userId: requireNonBlank(value.userId, "userId"),
    accessToken: requireNonBlank(value.accessToken, "accessToken"),
    refreshToken: requireNonBlank(value.refreshToken, "refreshToken"),
    accessTokenExpiresAt,
  };
}

function normalizeSecureStoreOptions(options: NativeSecureStoreOptions): NativeSecureStoreOptions {
  if (!isRecord(options)) {
    throw new TokenSessionError("AUTH_SECURE_STORE_INVALID", "secure store options must be an object");
  }

  return {
    namespace: requireString(options.namespace, "namespace").trim(),
    encryptedAtRest: requireBoolean(options.encryptedAtRest, "encryptedAtRest"),
    hardwareBacked: requireBoolean(options.hardwareBacked, "hardwareBacked"),
    deviceOnly: requireBoolean(options.deviceOnly, "deviceOnly"),
    biometricOrPasscodeRequired: requireBoolean(options.biometricOrPasscodeRequired, "biometricOrPasscodeRequired"),
    synchronizesToCloud: requireBoolean(options.synchronizesToCloud, "synchronizesToCloud"),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requireNonBlank(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim() === "") {
    throw new TokenSessionError("AUTH_SESSION_INVALID", `${field} is required`);
  }
  return value.trim();
}

function requireBoolean(value: unknown, field: string): boolean {
  if (typeof value !== "boolean") {
    throw new TokenSessionError("AUTH_SECURE_STORE_INVALID", `${field} must be a boolean`);
  }
  return value;
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== "string") {
    throw new TokenSessionError("AUTH_SECURE_STORE_INVALID", `${field} must be a string`);
  }
  return value;
}
