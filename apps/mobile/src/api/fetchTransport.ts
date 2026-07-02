import {
  ApiClientError,
  type ApiTransport,
  type ApiTransportRequest,
  type ApiTransportResponse,
} from "./voiceSecureApiClient.ts";

export interface FetchTransportInit {
  method?: string;
  headers?: Record<string, string>;
  body?: string;
}

export interface FetchHeadersLike {
  forEach(callback: (value: string, key: string) => void): void;
}

export interface FetchResponseLike {
  status: number;
  headers?: FetchHeadersLike;
  text(): Promise<string>;
}

export type FetchLike = (input: string, init: FetchTransportInit) => Promise<FetchResponseLike>;

export interface FetchApiTransportConfig {
  baseUrl: string;
  fetcher?: FetchLike;
}

export class FetchApiTransport implements ApiTransport {
  private readonly baseUrl: string;
  private readonly fetcher: FetchLike;

  constructor(config: FetchApiTransportConfig) {
    this.baseUrl = normalizeBaseUrl(config.baseUrl);
    this.fetcher = config.fetcher ?? defaultFetch;
  }

  async send(request: ApiTransportRequest): Promise<ApiTransportResponse> {
    try {
      const response = await this.fetcher(joinUrl(this.baseUrl, request.path), createFetchInit(request));
      return {
        status: response.status,
        headers: headersToRecord(response.headers),
        body: await response.text(),
      };
    } catch (error) {
      if (error instanceof ApiClientError) {
        throw error;
      }
      throw new ApiClientError(503, "NETWORK_UNAVAILABLE", "network unavailable");
    }
  }
}

function createFetchInit(request: ApiTransportRequest): FetchTransportInit {
  const init: FetchTransportInit = {
    method: request.method,
    headers: request.headers,
  };
  if (request.body !== undefined) {
    init.body = request.body;
  }
  return init;
}

async function defaultFetch(input: string, init: FetchTransportInit): Promise<FetchResponseLike> {
  const fetcher = globalThis.fetch;
  if (typeof fetcher !== "function") {
    throw new ApiClientError(503, "NETWORK_UNAVAILABLE", "network unavailable");
  }
  return (await fetcher(input, init)) as FetchResponseLike;
}

function normalizeBaseUrl(baseUrl: string): string {
  const trimmed = baseUrl.trim();
  if (trimmed === "") {
    throw new ApiClientError(400, "VALIDATION_FAILED", "baseUrl is required");
  }
  return trimmed.replace(/\/+$/, "");
}

function joinUrl(baseUrl: string, path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${baseUrl}${normalizedPath}`;
}

function headersToRecord(headers: FetchHeadersLike | undefined): Record<string, string> {
  const values: Record<string, string> = {};
  headers?.forEach((value, key) => {
    values[key] = value;
  });
  return values;
}
