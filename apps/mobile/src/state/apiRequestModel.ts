export type ApiRequestStatus = "idle" | "loading" | "succeeded" | "failed";

export interface ApiRequestError {
  status: number;
  code: string;
  message: string;
}

export interface ApiRequestState<TData> {
  status: ApiRequestStatus;
  data?: TData;
  error?: ApiRequestError;
  traceId?: string;
}

export function createIdleRequestState<TData>(): ApiRequestState<TData> {
  return { status: "idle" };
}

export function requestStarted<TData>(state: ApiRequestState<TData>, traceId: string): ApiRequestState<TData> {
  return {
    ...state,
    status: "loading",
    error: undefined,
    traceId,
  };
}

export function requestSucceeded<TData>(state: ApiRequestState<TData>, data: TData): ApiRequestState<TData> {
  return {
    ...state,
    status: "succeeded",
    data,
    error: undefined,
  };
}

export function requestFailed<TData>(state: ApiRequestState<TData>, error: ApiRequestError): ApiRequestState<TData> {
  return {
    ...state,
    status: "failed",
    error,
  };
}
