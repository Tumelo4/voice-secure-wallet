import { ApiClientError, type PaymentStartResult, type StartPaymentCommand, type WalletBalanceResult } from "../api/voiceSecureApiClient.ts";
import { TokenSessionError } from "../auth/tokenSession.ts";
import {
  createIdleRequestState,
  requestFailed,
  requestStarted,
  requestSucceeded,
  type ApiRequestError,
  type ApiRequestState,
} from "./apiRequestModel.ts";

export interface MobileApiClientPort {
  getWalletBalance(accountId: string): Promise<WalletBalanceResult>;
  startPayment(command: StartPaymentCommand): Promise<PaymentStartResult>;
}

export interface MobileApiState {
  walletBalance: ApiRequestState<WalletBalanceResult>;
  paymentStart: ApiRequestState<PaymentStartResult>;
}

export type MobileApiAction =
  | { type: "walletBalance/requested"; traceId: string }
  | { type: "walletBalance/succeeded"; payload: WalletBalanceResult }
  | { type: "walletBalance/failed"; error: ApiRequestError }
  | { type: "paymentStart/requested"; traceId: string }
  | { type: "paymentStart/succeeded"; payload: PaymentStartResult }
  | { type: "paymentStart/failed"; error: ApiRequestError };

export type MobileApiDispatch = (action: MobileApiAction) => void;
export type MobileApiThunk = (dispatch: MobileApiDispatch) => Promise<void>;
export type ReduxUnknownAction = { type: string; [key: string]: unknown };

export interface MobileApiFlowDependencies {
  client: MobileApiClientPort;
  traceIdFactory: () => string;
}

export function createMobileApiState(): MobileApiState {
  return {
    walletBalance: createIdleRequestState<WalletBalanceResult>(),
    paymentStart: createIdleRequestState<PaymentStartResult>(),
  };
}

export function mobileApiReducer(state: MobileApiState = createMobileApiState(), action: MobileApiAction | ReduxUnknownAction): MobileApiState {
  if (!isMobileApiAction(action)) {
    return state;
  }

  switch (action.type) {
    case "walletBalance/requested":
      return {
        ...state,
        walletBalance: requestStarted(state.walletBalance, action.traceId),
      };
    case "walletBalance/succeeded":
      return {
        ...state,
        walletBalance: requestSucceeded(state.walletBalance, action.payload),
      };
    case "walletBalance/failed":
      return {
        ...state,
        walletBalance: requestFailed(state.walletBalance, action.error),
      };
    case "paymentStart/requested":
      return {
        ...state,
        paymentStart: requestStarted(state.paymentStart, action.traceId),
      };
    case "paymentStart/succeeded":
      return {
        ...state,
        paymentStart: requestSucceeded(state.paymentStart, action.payload),
      };
    case "paymentStart/failed":
      return {
        ...state,
        paymentStart: requestFailed(state.paymentStart, action.error),
      };
    default:
      return state;
  }
}

function isMobileApiAction(action: MobileApiAction | ReduxUnknownAction): action is MobileApiAction {
  switch (action.type) {
    case "walletBalance/requested":
    case "paymentStart/requested":
      return typeof action.traceId === "string";
    case "walletBalance/succeeded":
    case "paymentStart/succeeded":
      return action.payload !== undefined;
    case "walletBalance/failed":
    case "paymentStart/failed":
      return action.error !== undefined;
    default:
      return false;
  }
}

export function loadWalletBalance(accountId: string, dependencies: MobileApiFlowDependencies): MobileApiThunk {
  return async (dispatch) => {
    dispatch({ type: "walletBalance/requested", traceId: dependencies.traceIdFactory() });
    try {
      const balance = await dependencies.client.getWalletBalance(accountId);
      dispatch({ type: "walletBalance/succeeded", payload: balance });
    } catch (error) {
      dispatch({ type: "walletBalance/failed", error: apiRequestErrorFrom(error) });
    }
  };
}

export function startPayment(command: StartPaymentCommand, dependencies: MobileApiFlowDependencies): MobileApiThunk {
  return async (dispatch) => {
    dispatch({ type: "paymentStart/requested", traceId: dependencies.traceIdFactory() });
    try {
      const result = await dependencies.client.startPayment(command);
      dispatch({ type: "paymentStart/succeeded", payload: result });
    } catch (error) {
      dispatch({ type: "paymentStart/failed", error: apiRequestErrorFrom(error) });
    }
  };
}

export const mobileApiSelectors = {
  walletBalanceStatus(state: Pick<MobileApiState, "walletBalance">): string {
    return state.walletBalance.status;
  },
  paymentStartStatus(state: Pick<MobileApiState, "paymentStart">): string {
    return state.paymentStart.status;
  },
};

export const selectMobileApi = (state: { mobileApi: MobileApiState }) => state.mobileApi;
export const selectWalletBalanceStatus = (state: { mobileApi: MobileApiState }) =>
  mobileApiSelectors.walletBalanceStatus(state.mobileApi);
export const selectPaymentStartStatus = (state: { mobileApi: MobileApiState }) =>
  mobileApiSelectors.paymentStartStatus(state.mobileApi);

function apiRequestErrorFrom(error: unknown): ApiRequestError {
  if (error instanceof ApiClientError) {
    return {
      status: error.status,
      code: error.code,
      message: error.message,
    };
  }
  if (error instanceof TokenSessionError) {
    return {
      status: 401,
      code: error.code,
      message: error.message,
    };
  }
  return {
    status: 500,
    code: "MOBILE_API_FLOW_FAILED",
    message: "mobile API flow failed",
  };
}
