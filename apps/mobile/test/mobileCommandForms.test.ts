import assert from "node:assert/strict";
import test from "node:test";

import type { PaymentStartResult, StartPaymentCommand, WalletBalanceResult } from "../src/api/voiceSecureApiClient.ts";
import {
  createPaymentCommandForm,
  createWalletBalanceCommandForm,
  paymentCommandFromForm,
  submitPaymentCommandForm,
  submitWalletBalanceCommandForm,
  updatePaymentCommandForm,
  updateWalletBalanceCommandForm,
  walletAccountIdFromForm,
} from "../src/state/mobileCommandForms.ts";
import {
  createMobileApiState,
  mobileApiReducer,
  type MobileApiAction,
  type MobileApiClientPort,
  type MobileApiState,
} from "../src/state/mobileApiSlice.ts";

test("wallet balance command form trims account id and dispatches the Redux flow", async () => {
  const client = new StubMobileApiClient();
  const dispatch = statefulDispatch();
  const form = updateWalletBalanceCommandForm(createWalletBalanceCommandForm(), "accountId", " wallet-123 ");

  assert.equal(walletAccountIdFromForm(form), "wallet-123");

  await submitWalletBalanceCommandForm(form, {
    client,
    traceIdFactory: () => "trace-wallet-form-1",
  })(dispatch.dispatch);

  assert.deepEqual(dispatch.actionTypes(), ["walletBalance/requested", "walletBalance/succeeded"]);
  assert.deepEqual(client.walletAccountIds, ["wallet-123"]);
  assert.equal(dispatch.state.walletBalance.status, "succeeded");
  assert.equal(dispatch.state.walletBalance.traceId, "trace-wallet-form-1");
});

test("payment command form builds a typed payment command and dispatches the Redux flow", async () => {
  const client = new StubMobileApiClient();
  const dispatch = statefulDispatch();
  const form = paymentForm();

  assert.deepEqual(paymentCommandFromForm(form), paymentCommand());

  await submitPaymentCommandForm(form, {
    client,
    traceIdFactory: () => "trace-payment-form-1",
  })(dispatch.dispatch);

  assert.deepEqual(dispatch.actionTypes(), ["paymentStart/requested", "paymentStart/succeeded"]);
  assert.deepEqual(client.paymentCommands, [paymentCommand()]);
  assert.equal(dispatch.state.paymentStart.status, "succeeded");
  assert.equal(dispatch.state.paymentStart.traceId, "trace-payment-form-1");
});

test("invalid command forms fail locally without calling the API client", async () => {
  const client = new StubMobileApiClient();
  const dispatch = statefulDispatch();
  const invalidPayment = updatePaymentCommandForm(paymentForm(), "amount", "0");

  await submitWalletBalanceCommandForm(createWalletBalanceCommandForm(), {
    client,
    traceIdFactory: () => "trace-wallet-invalid",
  })(dispatch.dispatch);
  await submitPaymentCommandForm(invalidPayment, {
    client,
    traceIdFactory: () => "trace-payment-invalid",
  })(dispatch.dispatch);

  assert.deepEqual(dispatch.actionTypes(), ["walletBalance/failed", "paymentStart/failed"]);
  assert.equal(dispatch.state.walletBalance.error?.code, "MOBILE_FORM_INVALID");
  assert.equal(dispatch.state.walletBalance.error?.message, "account id is required");
  assert.equal(dispatch.state.paymentStart.error?.code, "MOBILE_FORM_INVALID");
  assert.equal(dispatch.state.paymentStart.error?.message, "amount must be greater than zero");
  assert.deepEqual(client.walletAccountIds, []);
  assert.deepEqual(client.paymentCommands, []);
});

class StubMobileApiClient implements MobileApiClientPort {
  readonly walletAccountIds: string[] = [];
  readonly paymentCommands: StartPaymentCommand[] = [];

  async getWalletBalance(accountId: string): Promise<WalletBalanceResult> {
    this.walletAccountIds.push(accountId);
    return {
      accountId,
      currency: "ZAR",
      balance: 1250,
      version: 7,
      updatedAt: "2026-07-03T12:00:00Z",
    };
  }

  async startPayment(command: StartPaymentCommand): Promise<PaymentStartResult> {
    this.paymentCommands.push(command);
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

function paymentForm() {
  let form = createPaymentCommandForm();
  form = updatePaymentCommandForm(form, "sagaId", " saga-screen-1 ");
  form = updatePaymentCommandForm(form, "idempotencyKey", " idem-screen-1 ");
  form = updatePaymentCommandForm(form, "userId", " user-1 ");
  form = updatePaymentCommandForm(form, "fromAccountId", " wallet-from ");
  form = updatePaymentCommandForm(form, "toAccountId", " wallet-to ");
  form = updatePaymentCommandForm(form, "amount", "750.25");
  form = updatePaymentCommandForm(form, "currency", " zar ");
  return form;
}

function paymentCommand(): StartPaymentCommand {
  return {
    sagaId: "saga-screen-1",
    idempotencyKey: "idem-screen-1",
    userId: "user-1",
    fromAccountId: "wallet-from",
    toAccountId: "wallet-to",
    amount: 750.25,
    currency: "ZAR",
  };
}
