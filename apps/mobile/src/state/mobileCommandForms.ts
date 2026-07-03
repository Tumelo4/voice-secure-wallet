import type { StartPaymentCommand } from "../api/voiceSecureApiClient.ts";
import {
  loadWalletBalance,
  startPayment,
  type MobileApiAction,
  type MobileApiFlowDependencies,
  type MobileApiThunk,
} from "./mobileApiSlice.ts";

export interface WalletBalanceCommandForm {
  accountId: string;
}

export type WalletBalanceCommandField = keyof WalletBalanceCommandForm;

export interface PaymentCommandForm {
  sagaId: string;
  idempotencyKey: string;
  userId: string;
  fromAccountId: string;
  toAccountId: string;
  amount: string;
  currency: string;
}

export type PaymentCommandField = keyof PaymentCommandForm;

export function createWalletBalanceCommandForm(): WalletBalanceCommandForm {
  return {
    accountId: "",
  };
}

export function updateWalletBalanceCommandForm(
  form: WalletBalanceCommandForm,
  field: WalletBalanceCommandField,
  value: string
): WalletBalanceCommandForm {
  return {
    ...form,
    [field]: value,
  };
}

export function walletAccountIdFromForm(form: WalletBalanceCommandForm): string {
  const accountId = clean(form.accountId);
  if (accountId === "") {
    throw formError("account id is required");
  }
  return accountId;
}

export function createPaymentCommandForm(): PaymentCommandForm {
  return {
    sagaId: "",
    idempotencyKey: "",
    userId: "",
    fromAccountId: "",
    toAccountId: "",
    amount: "",
    currency: "ZAR",
  };
}

export function updatePaymentCommandForm(
  form: PaymentCommandForm,
  field: PaymentCommandField,
  value: string
): PaymentCommandForm {
  return {
    ...form,
    [field]: value,
  };
}

export function paymentCommandFromForm(form: PaymentCommandForm): StartPaymentCommand {
  return {
    sagaId: requiredText(form.sagaId, "saga id"),
    idempotencyKey: requiredText(form.idempotencyKey, "idempotency key"),
    userId: requiredText(form.userId, "user id"),
    fromAccountId: requiredText(form.fromAccountId, "from account id"),
    toAccountId: requiredText(form.toAccountId, "to account id"),
    amount: requiredPositiveAmount(form.amount),
    currency: requiredText(form.currency, "currency").toUpperCase(),
  };
}

export function submitWalletBalanceCommandForm(
  form: WalletBalanceCommandForm,
  dependencies: MobileApiFlowDependencies
): MobileApiThunk {
  try {
    return loadWalletBalance(walletAccountIdFromForm(form), dependencies);
  } catch (error) {
    return localFailure("walletBalance/failed", error);
  }
}

export function submitPaymentCommandForm(
  form: PaymentCommandForm,
  dependencies: MobileApiFlowDependencies
): MobileApiThunk {
  try {
    return startPayment(paymentCommandFromForm(form), dependencies);
  } catch (error) {
    return localFailure("paymentStart/failed", error);
  }
}

function localFailure(type: "walletBalance/failed" | "paymentStart/failed", error: unknown): MobileApiThunk {
  return async (dispatch) => {
    dispatch({
      type,
      error: {
        status: 400,
        code: "MOBILE_FORM_INVALID",
        message: error instanceof Error ? error.message : "mobile command form is invalid",
      },
    } as MobileApiAction);
  };
}

function requiredText(value: string, field: string): string {
  const nextValue = clean(value);
  if (nextValue === "") {
    throw formError(`${field} is required`);
  }
  return nextValue;
}

function requiredPositiveAmount(value: string): number {
  const amount = Number(clean(value));
  if (!Number.isFinite(amount)) {
    throw formError("amount must be a number");
  }
  if (amount <= 0) {
    throw formError("amount must be greater than zero");
  }
  return amount;
}

function clean(value: string): string {
  return value.trim();
}

function formError(message: string): Error {
  return new Error(message);
}
