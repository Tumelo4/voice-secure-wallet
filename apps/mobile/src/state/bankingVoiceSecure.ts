export type BankingTransactionIntent = "pay" | "send" | "topup";

export interface TransactionDraft {
  intent: BankingTransactionIntent;
  sourceAccountId: string;
  sourceAccount: string;
  beneficiaryId: string;
  recipient: string;
  amount: string;
  currency: string;
  note: string;
}

export type VoiceSecureStage = "listening" | "retry" | "fallback" | "confirmed";
export type VoiceSecureOutcome = "match" | "miss";

export interface VoiceSecureFlow {
  intent: BankingTransactionIntent;
  stage: VoiceSecureStage;
  attempts: number;
  prompt: string;
  message: string;
  statusLabel: string;
  fallbackOptions: Array<"PIN" | "Face ID" | "Fingerprint">;
}

export function createTransactionDraft(intent: BankingTransactionIntent = "pay"): TransactionDraft {
  return {
    intent,
    sourceAccountId: "",
    sourceAccount: "",
    beneficiaryId: "",
    recipient: "",
    amount: "750.00",
    currency: "ZAR",
    note: "Dinner split",
  };
}

export function createVoiceSecureFlow(draft: TransactionDraft): VoiceSecureFlow {
  return {
    intent: draft.intent,
    stage: "listening",
    attempts: 0,
    prompt: 'Say "Confirm payment" to continue',
    message: "Verifying your voice...",
    statusLabel: "Listening",
    fallbackOptions: ["PIN", "Face ID", "Fingerprint"],
  };
}

export function validateTransactionDraft(draft: TransactionDraft): string | null {
  if (!draft.sourceAccountId.trim() || !draft.sourceAccount.trim()) return "Choose a source account.";
  if (!draft.beneficiaryId.trim() || !draft.recipient.trim()) return "Choose a beneficiary.";
  const amount = draft.amount.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(amount) || !/[1-9]/.test(amount)) {
    return "Enter a valid amount with no more than two decimal places.";
  }
  if (!draft.note.trim()) return "Add a payment reference.";
  if (draft.note.trim().length > 80) return "Payment reference must be 80 characters or fewer.";
  return null;
}

export function advanceVoiceSecureFlow(flow: VoiceSecureFlow, outcome: VoiceSecureOutcome): VoiceSecureFlow {
  if (outcome === "match") {
    return {
      ...flow,
      stage: "confirmed",
      message: transactionSuccessMessage(flow.intent),
      statusLabel: transactionStatusLabel(flow.intent),
    };
  }

  const attempts = flow.attempts + 1;

  if (attempts >= 2) {
    return {
      ...flow,
      stage: "fallback",
      attempts,
      message: "We couldn't verify your voice. Try again or use PIN, Face ID, or fingerprint.",
      statusLabel: "Choose another way",
    };
  }

  return {
    ...flow,
    stage: "retry",
    attempts,
    message: "We couldn't verify your voice, try again.",
    statusLabel: "Try again",
  };
}

export function approveVoiceSecureFallback(flow: VoiceSecureFlow, method: "PIN" | "Face ID" | "Fingerprint"): VoiceSecureFlow {
  return {
    ...flow,
    stage: "confirmed",
    statusLabel: transactionStatusLabel(flow.intent),
    message: `Verified with ${method.toLowerCase()}. ${transactionSuccessMessage(flow.intent)}`,
  };
}

function transactionStatusLabel(intent: BankingTransactionIntent): string {
  switch (intent) {
    case "pay":
      return "Payment sent";
    case "send":
      return "Transfer sent";
    case "topup":
      return "Top up complete";
  }
}

function transactionSuccessMessage(intent: BankingTransactionIntent): string {
  switch (intent) {
    case "pay":
      return "Your payment is on its way.";
    case "send":
      return "Your transfer is on its way.";
    case "topup":
      return "Your top up is on its way.";
  }
}
