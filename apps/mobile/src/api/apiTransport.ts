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
