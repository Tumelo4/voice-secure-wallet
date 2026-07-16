import { StaticAccessTokenProvider, type AccessTokenProvider } from "../auth/tokenProvider.ts";

export interface ApiTransportRequest {
  method: "GET" | "POST";
  path: string;
  headers: Record<string, string>;
  body?: string;
}

export interface ApiTransportResponse {
  status: number;
  headers?: Record<string, string>;
  body: string;
}

export interface ApiTransport {
  send(request: ApiTransportRequest): Promise<ApiTransportResponse>;
}

export interface VoiceSecureApiClientConfig {
  token?: string;
  tokenProvider?: AccessTokenProvider;
  traceIdFactory: () => string;
  transport: ApiTransport;
}

export interface StartPaymentCommand {
  sourceAccountId: string;
  beneficiaryId: string;
  amount: {
    value: string;
    currency: string;
  };
  reference: string;
}

export interface PaymentStartResult {
  paymentReference: string;
  state: "AUTHORISATION_REQUIRED" | "PROCESSING" | "COMPLETED" | "FAILED" | "REVIEW_REQUIRED" | string;
  authPolicy: string;
  message: string;
}

export interface WalletBalanceResult {
  accountId: string;
  currency: string;
  balance: number;
  version: number;
  updatedAt: string;
}

export interface CustomerAccount {
  accountId: string;
  displayName: string;
  maskedAccountNumber: string;
  currency: string;
}

export interface CustomerAccountsResult {
  accounts: CustomerAccount[];
}

export interface BeneficiarySummary {
  beneficiaryId: string;
  displayName: string;
  maskedAccountNumber: string;
  currency: string;
  status: "PENDING_VERIFICATION" | "COOLING_OFF" | "ACTIVE" | "BLOCKED";
  availableAt: string;
}

export interface CustomerBeneficiariesResult {
  beneficiaries: BeneficiarySummary[];
}

export interface CreateBeneficiaryCommand {
  displayName: string;
  bankCode: string;
  accountNumber: string;
}

export interface VoiceChallengeCommand {
  paymentReference: string;
}

export interface VoiceChallengeResult {
  paymentReference: string;
  challengeId: string;
  phrase: string;
  expiresAt: string;
  authPolicy: string;
  transactionAmountMinor: number;
  transactionBindingHash: string;
}

export interface VoiceVerificationCommand {
  challenge: VoiceChallengeResult;
  capturedAt: string;
  audio: { contentBase64: string; codec: string; sampleRateHz: number };
}

export interface VoiceVerificationResult {
  verificationId: string;
  status: string;
  fallbackRequested: boolean;
  reason: string;
}

interface ApiErrorBody {
  code?: string;
  message?: string;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryAfter?: string;

  constructor(status: number, code: string, message: string, retryAfter?: string) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
    this.retryAfter = retryAfter;
  }
}

export class VoiceSecureApiClient {
  private readonly paymentAttemptKeys = new Map<string, string>();
  private readonly voiceAttemptKeys = new Map<string, string>();
  private readonly tokenProvider: AccessTokenProvider;
  private readonly traceIdFactory: () => string;
  private readonly transport: ApiTransport;

  constructor(config: VoiceSecureApiClientConfig) {
    this.tokenProvider = resolveTokenProvider(config);
    this.traceIdFactory = config.traceIdFactory;
    this.transport = config.transport;
  }

  async startPayment(command: StartPaymentCommand): Promise<PaymentStartResult> {
    const fingerprint = JSON.stringify(command);
    const idempotencyKey = this.paymentAttemptKeys.get(fingerprint) ?? createIdempotencyKey();
    this.paymentAttemptKeys.set(fingerprint, idempotencyKey);
    try {
      const result = await this.sendJson<PaymentStartResult>({
        method: "POST",
        path: "/v1/payments",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body: fingerprint,
      });
      this.paymentAttemptKeys.delete(fingerprint);
      return result;
    } catch (error) {
      if (error instanceof ApiClientError && error.status < 500) {
        this.paymentAttemptKeys.delete(fingerprint);
      }
      throw error;
    }
  }

  async getWalletBalance(accountId: string): Promise<WalletBalanceResult> {
    return this.sendJson<WalletBalanceResult>({
      method: "GET",
      path: `/wallets/${encodeURIComponent(requireNonBlank(accountId, "accountId"))}/balance`,
      headers: {},
    });
  }

  async getPaymentStatus(paymentReference: string): Promise<PaymentStartResult> {
    return this.sendJson<PaymentStartResult>({
      method: "GET",
      path: `/v1/payments/${encodeURIComponent(requireNonBlank(paymentReference, "paymentReference"))}`,
      headers: {},
    });
  }

  async getCustomerAccounts(): Promise<CustomerAccountsResult> {
    return this.sendJson<CustomerAccountsResult>({
      method: "GET",
      path: "/v1/me/accounts",
      headers: {},
    });
  }

  async getCustomerBeneficiaries(): Promise<CustomerBeneficiariesResult> {
    return this.sendJson<CustomerBeneficiariesResult>({ method: "GET", path: "/v1/me/beneficiaries", headers: {} });
  }

  async createBeneficiary(command: CreateBeneficiaryCommand): Promise<BeneficiarySummary> {
    return this.sendJson<BeneficiarySummary>({
      method: "POST",
      path: "/v1/me/beneficiaries",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(command),
    });
  }

  async issueVoiceChallenge(command: VoiceChallengeCommand): Promise<VoiceChallengeResult> {
    const challenge = await this.sendJson<Omit<VoiceChallengeResult, "paymentReference">>({
      method: "POST",
      path: `/v1/payments/${encodeURIComponent(requireNonBlank(command.paymentReference, "paymentReference"))}/voice-challenge`,
      headers: { "Content-Type": "application/json" },
      body: "{}",
    });
    return { ...challenge, paymentReference: command.paymentReference };
  }

  async verifyVoice(command: VoiceVerificationCommand): Promise<VoiceVerificationResult> {
    const challengeId = requireNonBlank(command.challenge.challengeId, "challengeId");
    const idempotencyKey = this.voiceAttemptKeys.get(challengeId) ?? createIdempotencyKey();
    this.voiceAttemptKeys.set(challengeId, idempotencyKey);
    try {
      const result = await this.sendJson<VoiceVerificationResult>({
        method: "POST",
        path: `/v1/voice/challenges/${encodeURIComponent(challengeId)}/verification`,
        headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({
          paymentReference: command.challenge.paymentReference,
          capturedAt: command.capturedAt,
          audio: command.audio,
        }),
      });
      this.voiceAttemptKeys.delete(challengeId);
      return result;
    } catch (error) {
      if (error instanceof ApiClientError && error.status < 500 && error.status !== 429) {
        this.voiceAttemptKeys.delete(challengeId);
      }
      throw error;
    }
  }

  private async sendJson<T>(request: Omit<ApiTransportRequest, "headers"> & { headers: Record<string, string> }): Promise<T> {
    const traceId = requireNonBlank(this.traceIdFactory(), "traceId");
    const token = requireNonBlank(await this.tokenProvider.getAccessToken(), "token");
    const response = await this.transport.send({
      ...request,
      headers: {
        ...request.headers,
        Authorization: `Bearer ${token}`,
        "X-Trace-Id": traceId,
      },
    });

    if (response.status < 200 || response.status >= 300) {
      throw errorFromResponse(response);
    }
    return parseJson<T>(response.body, "response body");
  }
}

function createIdempotencyKey(): string {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi && typeof cryptoApi.randomUUID === "function") {
    return cryptoApi.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === "x" ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function resolveTokenProvider(config: VoiceSecureApiClientConfig): AccessTokenProvider {
  if (config.tokenProvider) {
    return config.tokenProvider;
  }
  return new StaticAccessTokenProvider(requireNonBlank(config.token, "token"));
}

function errorFromResponse(response: ApiTransportResponse): ApiClientError {
  const body = parseJson<ApiErrorBody>(response.body, "error body");
  const code = body.code ?? "API_ERROR";
  const message = body.message ?? `request failed with status ${response.status}`;
  const retryAfter = response.headers?.["Retry-After"] ?? response.headers?.["retry-after"];
  return new ApiClientError(response.status, code, message, retryAfter);
}

function parseJson<T>(body: string, label: string): T {
  try {
    return JSON.parse(body) as T;
  } catch (error) {
    throw new ApiClientError(502, "INVALID_JSON", `invalid ${label}`);
  }
}

function requireNonBlank(value: string | null | undefined, field: string): string {
  if (value == null || value.trim() === "") {
    throw new ApiClientError(400, "VALIDATION_FAILED", `${field} is required`);
  }
  return value.trim();
}
