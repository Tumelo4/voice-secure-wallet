import assert from "node:assert/strict";
import test from "node:test";

import { ApiClientError, type PaymentStartResult, type StartPaymentCommand, type WalletBalanceResult } from "../src/api/voiceSecureApiClient.ts";
import { TokenSessionError } from "../src/auth/tokenSession.ts";
import {
  createMobileApiState,
  loadWalletBalance,
  mobileApiReducer,
  startPayment,
  type MobileApiAction,
  type MobileApiClientPort,
  type MobileApiState,
} from "../src/state/mobileApiSlice.ts";

test("mobile API state starts with idle wallet and payment requests", () => {
  const state = createMobileApiState();

  assert.equal(state.walletBalance.status, "idle");
  assert.equal(state.paymentStart.status, "idle");
});

test("wallet balance thunk dispatches loading then success state", async () => {
  const client = new StubMobileApiClient();
  const dispatch = statefulDispatch();

  await loadWalletBalance("wallet-1", {
    client,
    traceIdFactory: () => "trace-wallet-flow-1",
  })(dispatch.dispatch);

  assert.deepEqual(dispatch.actionTypes(), ["walletBalance/requested", "walletBalance/succeeded"]);
  assert.equal(client.walletAccountIds[0], "wallet-1");
  assert.equal(dispatch.state.walletBalance.status, "succeeded");
  assert.equal(dispatch.state.walletBalance.traceId, "trace-wallet-flow-1");
  assert.equal(dispatch.state.walletBalance.data?.balance, 1250);
});

test("payment start thunk dispatches loading then success state", async () => {
  const client = new StubMobileApiClient();
  const dispatch = statefulDispatch();
  const command: StartPaymentCommand = {
    sagaId: "saga-1",
    idempotencyKey: "idem-1",
    userId: "user-1",
    fromAccountId: "wallet-from",
    toAccountId: "wallet-to",
    amount: 750,
    currency: "ZAR",
  };

  await startPayment(command, {
    client,
    traceIdFactory: () => "trace-payment-flow-1",
  })(dispatch.dispatch);

  assert.deepEqual(dispatch.actionTypes(), ["paymentStart/requested", "paymentStart/succeeded"]);
  assert.equal(client.paymentCommands[0], command);
  assert.equal(dispatch.state.paymentStart.status, "succeeded");
  assert.equal(dispatch.state.paymentStart.traceId, "trace-payment-flow-1");
  assert.equal(dispatch.state.paymentStart.data?.state, "VOICE_VERIFICATION_PENDING");
});

test("API client errors become Redux-friendly failed request state", async () => {
  const client = new StubMobileApiClient({
    walletError: new ApiClientError(429, "RATE_LIMITED", "rate limit exceeded"),
  });
  const dispatch = statefulDispatch({
    walletBalance: {
      status: "succeeded",
      data: walletBalance(),
      traceId: "trace-previous",
    },
    paymentStart: { status: "idle" },
  });

  await loadWalletBalance("wallet-1", {
    client,
    traceIdFactory: () => "trace-wallet-flow-2",
  })(dispatch.dispatch);

  assert.deepEqual(dispatch.actionTypes(), ["walletBalance/requested", "walletBalance/failed"]);
  assert.equal(dispatch.state.walletBalance.status, "failed");
  assert.equal(dispatch.state.walletBalance.traceId, "trace-wallet-flow-2");
  assert.equal(dispatch.state.walletBalance.data?.balance, 1250);
  assert.equal(dispatch.state.walletBalance.error?.status, 429);
  assert.equal(dispatch.state.walletBalance.error?.code, "RATE_LIMITED");
});

test("auth session errors become unauthorized failed request state", async () => {
  const client = new StubMobileApiClient({
    paymentError: new TokenSessionError("AUTH_SESSION_REQUIRED", "mobile auth session is required"),
  });
  const dispatch = statefulDispatch();

  await startPayment(paymentCommand(), {
    client,
    traceIdFactory: () => "trace-payment-flow-2",
  })(dispatch.dispatch);

  assert.deepEqual(dispatch.actionTypes(), ["paymentStart/requested", "paymentStart/failed"]);
  assert.equal(dispatch.state.paymentStart.status, "failed");
  assert.equal(dispatch.state.paymentStart.error?.status, 401);
  assert.equal(dispatch.state.paymentStart.error?.code, "AUTH_SESSION_REQUIRED");
  assert.equal(dispatch.state.paymentStart.error?.message, "mobile auth session is required");
});

class StubMobileApiClient implements MobileApiClientPort {
  readonly walletAccountIds: string[] = [];
  readonly paymentCommands: StartPaymentCommand[] = [];
  private readonly walletError?: unknown;
  private readonly paymentError?: unknown;

  constructor(config: { walletError?: unknown; paymentError?: unknown } = {}) {
    this.walletError = config.walletError;
    this.paymentError = config.paymentError;
  }

  async getWalletBalance(accountId: string): Promise<WalletBalanceResult> {
    this.walletAccountIds.push(accountId);
    if (this.walletError) {
      throw this.walletError;
    }
    return walletBalance();
  }

  async startPayment(command: StartPaymentCommand): Promise<PaymentStartResult> {
    this.paymentCommands.push(command);
    if (this.paymentError) {
      throw this.paymentError;
    }
    return {
      sagaId: command.sagaId,
      state: "VOICE_VERIFICATION_PENDING",
      traceId: "trace-payment-runtime-1",
      authPolicy: "VOICE_OTP",
      eventCount: 4,
    };
  }
}

function statefulDispatch(initialState: MobileApiState = createMobileApiState()) {
  let state = initialState;
  const actions: MobileApiAction[] = [];
  return {
    get state() {
      return state;
    },
    dispatch(action: MobileApiAction) {
      actions.push(action);
      state = mobileApiReducer(state, action);
    },
    actionTypes() {
      return actions.map((action) => action.type);
    },
  };
}

function walletBalance(): WalletBalanceResult {
  return {
    accountId: "wallet-1",
    currency: "ZAR",
    balance: 1250,
    version: 7,
    updatedAt: "2026-07-02T12:00:00Z",
  };
}

function paymentCommand(): StartPaymentCommand {
  return {
    sagaId: "saga-1",
    idempotencyKey: "idem-1",
    userId: "user-1",
    fromAccountId: "wallet-from",
    toAccountId: "wallet-to",
    amount: 750,
    currency: "ZAR",
  };
}
