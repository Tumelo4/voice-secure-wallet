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
  sourceAccountId: string;
  beneficiaryId: string;
  amount: string;
  currency: string;
  reference: string;
}

export type PaymentCommandField = keyof PaymentCommandForm;

export function createWalletBalanceCommandForm(): WalletBalanceCommandForm {
  return {
    accountId: "11111111-1111-4111-8111-111111111111",
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
    sourceAccountId: "11111111-1111-4111-8111-111111111111",
    beneficiaryId: "22222222-2222-4222-8222-222222222222",
    amount: "",
    currency: "ZAR",
    reference: "",
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
    sourceAccountId: requiredText(form.sourceAccountId, "source account"),
    beneficiaryId: requiredText(form.beneficiaryId, "beneficiary"),
    amount: {
      value: requiredPositiveAmount(form.amount),
      currency: requiredText(form.currency, "currency").toUpperCase(),
    },
    reference: requiredText(form.reference, "reference"),
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

function requiredPositiveAmount(value: string): string {
  const cleaned = clean(value);
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) {
    throw formError("amount must be a number");
  }
  const [whole, fraction = ""] = cleaned.split(".");
  const normalizedWhole = whole.replace(/^0+(?=\d)/, "");
  const normalizedFraction = fraction.padEnd(2, "0");
  if (normalizedWhole === "0" && normalizedFraction === "00") {
    throw formError("amount must be greater than zero");
  }
  return `${normalizedWhole}.${normalizedFraction}`;
}

function clean(value: string): string {
  return value.trim();
}

function formError(message: string): Error {
  return new Error(message);
}
