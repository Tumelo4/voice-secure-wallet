import type { AccessTokenProvider } from "../auth/tokenProvider.ts";
import {
  ApiClientError,
  type ApiTransport,
  type ApiTransportRequest,
  type ApiTransportResponse,
} from "./apiTransport.ts";

export interface AuthenticatedJsonApiConfig {
  tokenProvider: AccessTokenProvider;
  traceIdFactory: () => string;
  transport: ApiTransport;
}

export type JsonApiRequest = Omit<ApiTransportRequest, "headers"> & {
  headers: Record<string, string>;
};

/** Adds cross-cutting authentication and trace headers, then maps the HTTP result. */
export class AuthenticatedJsonApi {
  private readonly config: AuthenticatedJsonApiConfig;

  constructor(config: AuthenticatedJsonApiConfig) {
    this.config = config;
  }

  async send<T>(request: JsonApiRequest): Promise<T> {
    const traceId = requireNonBlank(this.config.traceIdFactory(), "traceId");
    const token = requireNonBlank(await this.config.tokenProvider.getAccessToken(), "token");
    const response = await this.config.transport.send({
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

interface ApiErrorBody {
  code?: string;
  message?: string;
}

function errorFromResponse(response: ApiTransportResponse): ApiClientError {
  const body = parseJson<ApiErrorBody>(response.body, "error body");
  const retryAfter = response.headers?.["Retry-After"] ?? response.headers?.["retry-after"];
  return new ApiClientError(
    response.status,
    body.code ?? "API_ERROR",
    body.message ?? `request failed with status ${response.status}`,
    retryAfter,
  );
}

function parseJson<T>(body: string, label: string): T {
  try {
    return JSON.parse(body) as T;
  } catch {
    throw new ApiClientError(502, "INVALID_JSON", `invalid ${label}`);
  }
}

function requireNonBlank(value: string | null | undefined, field: string): string {
  if (value == null || value.trim() === "") {
    throw new ApiClientError(400, "VALIDATION_FAILED", `${field} is required`);
  }
  return value.trim();
}
