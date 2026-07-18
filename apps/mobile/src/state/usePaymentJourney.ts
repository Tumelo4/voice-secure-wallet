import { useEffect, useRef, useState } from "react";
import type { BeneficiarySummary, CustomerAccount, VoiceSecureApiClient } from "../api/voiceSecureApiClient";
import { VoiceCaptureSession, type VoiceRecorder } from "../voice/voiceCaptureSession";
import {
  advanceVoiceSecureFlow,
  createTransactionDraft,
  createVoiceSecureFlow,
  type BankingTransactionIntent,
  type TransactionDraft,
  type VoiceSecureFlow,
} from "./bankingVoiceSecure";
import type { BankingTabKey } from "../components/bankingDashboardContent";

export function usePaymentJourney(apiClient: VoiceSecureApiClient, voiceRecorder: VoiceRecorder) {
  const [activeTab, setActiveTab] = useState<BankingTabKey>("home");
  const [draft, setDraft] = useState<TransactionDraft>(() => createTransactionDraft("pay"));
  const [flow, setFlow] = useState<VoiceSecureFlow | null>(null);
  const [reviewingPayment, setReviewingPayment] = useState(false);
  const [accounts, setAccounts] = useState<CustomerAccount[]>([]);
  const [beneficiaries, setBeneficiaries] = useState<BeneficiarySummary[]>([]);
  const [optionsMessage, setOptionsMessage] = useState("Loading your accounts and beneficiaries…");
  const [submissionMessage, setSubmissionMessage] = useState<string | null>(null);
  const [paymentReference, setPaymentReference] = useState<string | null>(null);
  const [submittingPayment, setSubmittingPayment] = useState(false);
  const paymentSubmissionActive = useRef(false);
  const voiceSession = useRef<VoiceCaptureSession | null>(null);

  useEffect(() => {
    let active = true;
    void Promise.all([apiClient.getCustomerAccounts(), apiClient.getCustomerBeneficiaries()])
      .then(([accountResult, beneficiaryResult]) => {
        if (!active) return;
        const availableBeneficiaries = beneficiaryResult.beneficiaries.filter((item) => item.status === "ACTIVE");
        setAccounts(accountResult.accounts);
        setBeneficiaries(availableBeneficiaries);
        setOptionsMessage(availableBeneficiaries.length ? "Ready to pay securely." : "No active beneficiaries are available.");
        setDraft((current) => {
          const account = accountResult.accounts[0];
          const beneficiary = availableBeneficiaries[0];
          return {
            ...current,
            sourceAccountId: account?.accountId ?? "",
            sourceAccount: account ? `${account.displayName} ${account.maskedAccountNumber}` : "",
            beneficiaryId: beneficiary?.beneficiaryId ?? "",
            recipient: beneficiary?.displayName ?? "",
            currency: account?.currency ?? "ZAR",
          };
        });
      })
      .catch(() => {
        if (active) setOptionsMessage("Sign in or reconnect to load your payment options.");
      });
    return () => { active = false; };
  }, [apiClient]);

  const openPayments = (intent: BankingTransactionIntent) => {
    setDraft((current) => ({ ...current, intent }));
    setActiveTab("payments");
    setFlow(null);
    setReviewingPayment(false);
  };

  const refreshPaymentStatus = async () => {
    if (!paymentReference) return;
    try {
      const result = await apiClient.getPaymentStatus(paymentReference);
      if (result.state === "COMPLETED") {
        setFlow((current) => (current ? advanceVoiceSecureFlow(current, "match") : current));
        setSubmissionMessage(`Payment completed. Reference ${paymentReference}.`);
      } else if (["FAILED", "VOICE_REJECTED", "VOICE_FALLBACK_FAILED"].includes(result.state)) {
        setFlow((current) => (current ? advanceVoiceSecureFlow(current, "miss") : current));
        setSubmissionMessage("The payment was not authorized. Try again or use the offered fallback.");
      } else {
        setSubmissionMessage(`Payment is ${customerStateLabel(result.state)}. Reference ${paymentReference}.`);
      }
    } catch {
      setSubmissionMessage("We couldn't refresh this payment yet. It remains pending; try again shortly.");
    }
  };

  const beginVoiceSecure = async (nextDraft: TransactionDraft = draft) => {
    if (paymentSubmissionActive.current) return;
    paymentSubmissionActive.current = true;
    setSubmittingPayment(true);
    setSubmissionMessage("Creating your payment securely…");
    setDraft(nextDraft);
    try {
      const result = await apiClient.startPayment({
        sourceAccountId: nextDraft.sourceAccountId,
        beneficiaryId: nextDraft.beneficiaryId,
        amount: { value: nextDraft.amount, currency: nextDraft.currency },
        reference: nextDraft.note,
      });
      setReviewingPayment(false);
      setSubmissionMessage(`${result.message} Reference ${result.paymentReference}.`);
      setPaymentReference(result.paymentReference);
      const challenge = await apiClient.issueVoiceChallenge({ paymentReference: result.paymentReference });
      const session = new VoiceCaptureSession(apiClient, voiceRecorder, challenge);
      voiceSession.current = session;
      await session.begin();
      const nextFlow = createVoiceSecureFlow(nextDraft);
      if (session.state.status === "recording") {
        setFlow({ ...nextFlow, prompt: `Say "${challenge.phrase}" to continue`, message: "Listening securely…", statusLabel: "Recording" });
      } else {
        setFlow(advanceVoiceSecureFlow(advanceVoiceSecureFlow(nextFlow, "miss"), "miss"));
        setSubmissionMessage(session.state.status === "failed" ? session.state.message : "Voice capture is unavailable. Use fallback MFA.");
      }
    } catch {
      setSubmissionMessage("We couldn't start this payment. Check your connection and try again.");
    } finally {
      paymentSubmissionActive.current = false;
      setSubmittingPayment(false);
    }
  };

  const submitVoiceRecording = async () => {
    const session = voiceSession.current;
    if (!session) return;
    await session.submit();
    if (session.state.status === "completed") {
      setSubmissionMessage("Voice evidence submitted. Waiting for the server payment decision…");
      await refreshPaymentStatus();
    } else if (session.state.status === "retryable") {
      setSubmissionMessage(`${session.state.message} The same idempotent upload can be retried.`);
    } else if (session.state.status === "failed") {
      setFlow((current) => current ? advanceVoiceSecureFlow(current, "miss") : current);
      setSubmissionMessage(session.state.message);
    }
  };

  const retryVoice = () => {
    if (voiceSession.current?.state.status === "retryable") {
      void submitVoiceRecording();
      return;
    }
    void voiceSession.current?.cancel();
    setFlow((current) => (current ? advanceVoiceSecureFlow(current, "miss") : current));
  };

  const useFallback = (method: "PIN" | "Face ID" | "Fingerprint") => {
    setSubmissionMessage(`${method} verification requested. The payment remains pending until the server confirms it.`);
    void refreshPaymentStatus();
  };

  const resetPayment = (tab: BankingTabKey = "home") => {
    setFlow(null);
    setReviewingPayment(false);
    setSubmissionMessage(null);
    setPaymentReference(null);
    setActiveTab(tab);
  };

  return {
    activeTab, accounts, beneficiaries, draft, flow, optionsMessage, reviewingPayment,
    submissionMessage, submittingPayment, beginVoiceSecure, openPayments, resetPayment,
    retryVoice, setActiveTab, setDraft, setReviewingPayment, submitVoiceRecording, useFallback,
  };
}

function customerStateLabel(state: string): string {
  return state.toLowerCase().replaceAll("_", " ");
}
