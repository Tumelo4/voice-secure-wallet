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
  sagaId: string;
  idempotencyKey: string;
  userId: string;
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
}

export interface PaymentStartResult {
  sagaId: string;
  state: "VOICE_VERIFICATION_PENDING" | string;
  traceId: string;
  authPolicy: string;
  eventCount: number;
}

export interface WalletBalanceResult {
  accountId: string;
  currency: string;
  balance: number;
  version: number;
  updatedAt: string;
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
  private readonly tokenProvider: AccessTokenProvider;
  private readonly traceIdFactory: () => string;
  private readonly transport: ApiTransport;

  constructor(config: VoiceSecureApiClientConfig) {
    this.tokenProvider = resolveTokenProvider(config);
    this.traceIdFactory = config.traceIdFactory;
    this.transport = config.transport;
  }

  async startPayment(command: StartPaymentCommand): Promise<PaymentStartResult> {
    const { idempotencyKey, ...body } = command;
    return this.sendJson<PaymentStartResult>({
      method: "POST",
      path: "/payments",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": requireNonBlank(idempotencyKey, "idempotencyKey"),
      },
      body: JSON.stringify(body),
    });
  }

  async getWalletBalance(accountId: string): Promise<WalletBalanceResult> {
    return this.sendJson<WalletBalanceResult>({
      method: "GET",
      path: `/wallets/${encodeURIComponent(requireNonBlank(accountId, "accountId"))}/balance`,
      headers: {},
    });
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
