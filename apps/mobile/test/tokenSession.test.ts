import assert from "node:assert/strict";
import test from "node:test";

import {
  NativeSecureTokenStore,
  SecureStoreReadinessValidator,
  SecureTokenVault,
  TokenSessionAccessTokenProvider,
  TokenSessionError,
  type NativeSecureStoreDriver,
  type NativeSecureStoreOptions,
  type SecureTokenStore,
  type TokenRefreshPort,
  type TokenSession,
} from "../src/auth/tokenSession.ts";

test("secure token vault saves, loads, and clears the mobile session", async () => {
  const store = new MemorySecureStore();
  const vault = new SecureTokenVault({ store });
  const session = tokenSession({ accessToken: "access-one", refreshToken: "refresh-one" });

  await vault.save(session);

  const raw = await store.getItem("voiceSecure.tokenSession");
  assert.deepEqual(JSON.parse(raw ?? "{}"), session);
  assert.deepEqual(await vault.load(), session);

  await vault.clear();

  assert.equal(await vault.load(), null);
});

test("secure token vault clears corrupt session payloads", async () => {
  const store = new MemorySecureStore();
  const vault = new SecureTokenVault({ store });
  await store.setItem("voiceSecure.tokenSession", "not-json");

  await assert.rejects(
    () => vault.load(),
    (error) => {
      assert.ok(error instanceof TokenSessionError);
      assert.equal(error.code, "AUTH_SESSION_CORRUPT");
      return true;
    }
  );
  assert.equal(await store.getItem("voiceSecure.tokenSession"), null);
});

test("native secure token store writes with hardened mobile options", async () => {
  const driver = new RecordingNativeSecureStoreDriver();
  const options = nativeSecureStoreOptions();
  const store = new NativeSecureTokenStore({ driver, options });

  await store.setItem("voiceSecure.tokenSession", "session-json");
  assert.deepEqual(driver.setCalls, [{ key: "voiceSecure.tokenSession", value: "session-json", options }]);

  assert.equal(await store.getItem("voiceSecure.tokenSession"), "session-json");

  await store.deleteItem("voiceSecure.tokenSession");
  assert.deepEqual(driver.deleteCalls, [{ key: "voiceSecure.tokenSession", options }]);
});

test("secure store readiness blocks non-production mobile storage", () => {
  const report = new SecureStoreReadinessValidator().validate({
    ...nativeSecureStoreOptions(),
    encryptedAtRest: false,
    hardwareBacked: false,
    deviceOnly: false,
    biometricOrPasscodeRequired: false,
    synchronizesToCloud: true,
    namespace: "",
  });

  assert.equal(report.ready, false);
  assert.ok(report.blockers.includes("native secure storage must encrypt token sessions at rest"));
  assert.ok(report.blockers.includes("native secure storage must use hardware-backed keys"));
  assert.ok(report.blockers.includes("token sessions must stay on this device only"));
  assert.ok(report.blockers.includes("biometric or device passcode access must protect refresh tokens"));
  assert.ok(report.blockers.includes("token sessions must not sync to cloud backups"));
  assert.ok(report.blockers.includes("secure storage namespace is required"));
});

test("session token provider returns cached access tokens before refresh window", async () => {
  const vault = new RecordingTokenVault(tokenSession({ accessToken: "cached-access", refreshToken: "refresh-one" }));
  const provider = new TokenSessionAccessTokenProvider({
    vault,
    refresh: async () => tokenSession({ accessToken: "unused-access", refreshToken: "unused-refresh" }),
    clock: () => new Date("2026-07-02T10:00:00Z"),
    refreshWindowMs: 60_000,
  });

  const token = await provider.getAccessToken();

  assert.equal(token, "cached-access");
  assert.equal(vault.savedSessions.length, 0);
});

test("session token provider refreshes expiring access tokens and persists the new session", async () => {
  const existing = tokenSession({
    accessToken: "old-access",
    refreshToken: "old-refresh",
    accessTokenExpiresAt: "2026-07-02T10:00:30Z",
  });
  const refreshed = tokenSession({
    accessToken: "new-access",
    refreshToken: "new-refresh",
    accessTokenExpiresAt: "2026-07-02T10:20:00Z",
  });
  const vault = new RecordingTokenVault(existing);
  const refreshCalls: string[] = [];
  const refresh: TokenRefreshPort = async (refreshToken) => {
    refreshCalls.push(refreshToken);
    return refreshed;
  };
  const provider = new TokenSessionAccessTokenProvider({
    vault,
    refresh,
    clock: () => new Date("2026-07-02T10:00:00Z"),
    refreshWindowMs: 60_000,
  });

  const token = await provider.getAccessToken();

  assert.equal(token, "new-access");
  assert.deepEqual(refreshCalls, ["old-refresh"]);
  assert.deepEqual(vault.savedSessions, [refreshed]);
});

test("session token provider clears stored credentials when refresh fails", async () => {
  const vault = new RecordingTokenVault(
    tokenSession({
      accessToken: "expired-access",
      refreshToken: "refresh-one",
      accessTokenExpiresAt: "2026-07-02T09:59:00Z",
    })
  );
  const provider = new TokenSessionAccessTokenProvider({
    vault,
    refresh: async () => {
      throw new Error("refresh service unavailable");
    },
    clock: () => new Date("2026-07-02T10:00:00Z"),
  });

  await assert.rejects(
    () => provider.getAccessToken(),
    (error) => {
      assert.ok(error instanceof TokenSessionError);
      assert.equal(error.code, "AUTH_REFRESH_FAILED");
      return true;
    }
  );
  assert.equal(vault.cleared, true);
});

class MemorySecureStore implements SecureTokenStore {
  readonly values = new Map<string, string>();

  async getItem(key: string): Promise<string | null> {
    return this.values.get(key) ?? null;
  }

  async setItem(key: string, value: string): Promise<void> {
    this.values.set(key, value);
  }

  async deleteItem(key: string): Promise<void> {
    this.values.delete(key);
  }
}

class RecordingNativeSecureStoreDriver implements NativeSecureStoreDriver {
  readonly values = new Map<string, string>();
  readonly setCalls: Array<{ key: string; value: string; options: NativeSecureStoreOptions }> = [];
  readonly deleteCalls: Array<{ key: string; options: NativeSecureStoreOptions }> = [];

  async getItem(key: string, options: NativeSecureStoreOptions): Promise<string | null> {
    this.setCalls.push({ key, value: "__read__", options });
    return this.values.get(key) ?? null;
  }

  async setItem(key: string, value: string, options: NativeSecureStoreOptions): Promise<void> {
    this.setCalls.push({ key, value, options });
    this.values.set(key, value);
  }

  async deleteItem(key: string, options: NativeSecureStoreOptions): Promise<void> {
    this.deleteCalls.push({ key, options });
    this.values.delete(key);
  }
}

class RecordingTokenVault {
  readonly savedSessions: TokenSession[] = [];
  cleared = false;
  private session: TokenSession | null;

  constructor(session: TokenSession | null) {
    this.session = session;
  }

  async load(): Promise<TokenSession | null> {
    return this.session;
  }

  async save(session: TokenSession): Promise<void> {
    this.savedSessions.push(session);
    this.session = session;
  }

  async clear(): Promise<void> {
    this.cleared = true;
    this.session = null;
  }
}

function tokenSession(overrides: Partial<TokenSession> = {}): TokenSession {
  return {
    userId: "user-1",
    accessToken: "access-token",
    refreshToken: "refresh-token",
    accessTokenExpiresAt: "2026-07-02T10:10:00Z",
    ...overrides,
  };
}

function nativeSecureStoreOptions(): NativeSecureStoreOptions {
  return {
    namespace: "voiceSecure.tokenSession",
    encryptedAtRest: true,
    hardwareBacked: true,
    deviceOnly: true,
    biometricOrPasscodeRequired: true,
    synchronizesToCloud: false,
  };
}
