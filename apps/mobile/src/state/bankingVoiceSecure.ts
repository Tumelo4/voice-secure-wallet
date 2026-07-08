export type BankingTransactionIntent = "pay" | "send" | "topup";

export interface TransactionDraft {
  intent: BankingTransactionIntent;
  recipient: string;
  amount: string;
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
    recipient: "Maya Nkosi",
    amount: "750.00",
    note: "Dinner split",
  };
}

export function createVoiceSecureFlow(draft: TransactionDraft): VoiceSecureFlow {
  return {
    intent: draft.intent,
    stage: "listening",
    attempts: 0,
    prompt: voiceSecurePrompt(draft.intent),
    message: "Verifying your voice...",
    statusLabel: "Listening",
    fallbackOptions: ["PIN", "Face ID", "Fingerprint"],
  };
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

function voiceSecurePrompt(intent: BankingTransactionIntent): string {
  switch (intent) {
    case "pay":
      return 'Say "Confirm payment" to continue';
    case "send":
      return 'Say "Confirm transfer" to continue';
    case "topup":
      return 'Say "Confirm top up" to continue';
  }
}
