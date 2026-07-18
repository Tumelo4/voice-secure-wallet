import { render, screen, userEvent, waitFor } from "@testing-library/react-native";
import { ReadinessDashboard } from "../src/components/ReadinessDashboard";
import type { VoiceSecureApiClient } from "../src/api/voiceSecureApiClient";
import type { VoiceRecorder } from "../src/voice/voiceCaptureSession";

jest.mock("react-native-safe-area-context", () => ({
  useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 0, left: 0 }),
}));

describe("customer payment journey", () => {
  it("uses customer-safe choices and waits for the server to confirm voice authorization", async () => {
    const apiClient = {
      getCustomerAccounts: jest.fn().mockResolvedValue({
        accounts: [{ accountId: "acct-safe", displayName: "Everyday account", maskedAccountNumber: "•••• 4321", currency: "ZAR" }],
      }),
      getCustomerBeneficiaries: jest.fn().mockResolvedValue({
        beneficiaries: [{ beneficiaryId: "bene-safe", displayName: "Amina Dlamini", maskedAccountNumber: "•••• 7788", currency: "ZAR", status: "ACTIVE", availableAt: "2026-07-13T00:00:00Z" }],
      }),
      startPayment: jest.fn().mockResolvedValue({
        paymentReference: "pay_public_123",
        state: "AUTHORISATION_REQUIRED",
        authPolicy: "VOICE_OR_MFA",
        message: "Payment created",
      }),
      issueVoiceChallenge: jest.fn().mockResolvedValue({ paymentReference: "pay_public_123", challengeId: "challenge-1", phrase: "Confirm payment", expiresAt: "2099-01-01T00:00:00Z", authPolicy: "VOICE_OTP", transactionAmountMinor: 75000, transactionBindingHash: "binding" }),
      verifyVoice: jest.fn().mockResolvedValue({ verificationId: "v-1", status: "VERIFIED", fallbackRequested: false, reason: "accepted" }),
      getPaymentStatus: jest.fn().mockResolvedValue({
        paymentReference: "pay_public_123",
        state: "COMPLETED",
        authPolicy: "VOICE_OR_MFA",
        message: "Payment completed",
      }),
    } as unknown as VoiceSecureApiClient;
    const user = userEvent.setup();

    await render(<ReadinessDashboard apiClient={apiClient} voiceRecorder={recorder()} />);
    await user.press(screen.getByRole("tab", { name: "Payments" }));

    expect(await screen.findByText("Ready to pay securely.")).toBeOnTheScreen();
    expect(screen.getByRole("radio", { name: /Everyday account/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /Amina Dlamini/ })).toBeChecked();
    expect(screen.queryByText("acct-safe")).not.toBeOnTheScreen();
    expect(screen.queryByText("bene-safe")).not.toBeOnTheScreen();

    await user.press(screen.getByRole("button", { name: "Review payment" }));
    expect(await screen.findByText("Confirm the details")).toBeOnTheScreen();
    expect(screen.getAllByText("R 750.00")).toHaveLength(2);

    await user.press(screen.getByRole("button", { name: "Try voice demo" }));
    expect(await screen.findAllByText("Listening securely…")).toHaveLength(1);
    expect(apiClient.startPayment).toHaveBeenCalledWith({
      sourceAccountId: "acct-safe",
      beneficiaryId: "bene-safe",
      amount: { value: "750.00", currency: "ZAR" },
      reference: "Dinner split",
    });

    await user.press(screen.getByRole("button", { name: "Submit recording" }));
    expect((await screen.findAllByText("Payment sent")).length).toBeGreaterThanOrEqual(1);
    await waitFor(() => expect(apiClient.getPaymentStatus).toHaveBeenCalledWith("pay_public_123"));
  });

  it("keeps pending authorization distinct and exposes accessible fallback MFA", async () => {
    const apiClient = {
      getCustomerAccounts: jest.fn().mockResolvedValue({
        accounts: [{ accountId: "acct-safe", displayName: "Everyday account", maskedAccountNumber: "•••• 4321", currency: "ZAR" }],
      }),
      getCustomerBeneficiaries: jest.fn().mockResolvedValue({
        beneficiaries: [{ beneficiaryId: "bene-safe", displayName: "Amina Dlamini", maskedAccountNumber: "•••• 7788", currency: "ZAR", status: "ACTIVE", availableAt: "2026-07-13T00:00:00Z" }],
      }),
      startPayment: jest.fn().mockResolvedValue({ paymentReference: "pay_pending_123", state: "AUTHORISATION_REQUIRED", authPolicy: "VOICE_OR_MFA", message: "Payment created" }),
      issueVoiceChallenge: jest.fn().mockResolvedValue({ paymentReference: "pay_pending_123", challengeId: "challenge-2", phrase: "Confirm payment", expiresAt: "2099-01-01T00:00:00Z", authPolicy: "VOICE_OTP", transactionAmountMinor: 75000, transactionBindingHash: "binding" }),
      verifyVoice: jest.fn().mockResolvedValue({ verificationId: "v-2", status: "VERIFIED", fallbackRequested: false, reason: "accepted" }),
      getPaymentStatus: jest.fn().mockResolvedValue({
        paymentReference: "pay_pending_123",
        state: "PROCESSING",
        authPolicy: "VOICE_OR_MFA",
        message: "Payment processing",
      }),
    } as unknown as VoiceSecureApiClient;
    const user = userEvent.setup();

    await render(<ReadinessDashboard apiClient={apiClient} voiceRecorder={recorder()} />);
    await user.press(screen.getByRole("tab", { name: "Payments" }));
    expect(await screen.findByText("Ready to pay securely.")).toBeOnTheScreen();
    await user.press(screen.getByRole("button", { name: "Review payment" }));

    await user.press(screen.getByRole("button", { name: "Try voice demo" }));
    expect(apiClient.startPayment).toHaveBeenCalledTimes(1);

    expect(await screen.findByRole("button", { name: "Submit recording" })).toBeOnTheScreen();
    await user.press(screen.getByRole("button", { name: "Submit recording" }));
    expect(await screen.findByText(/Payment is processing/)).toBeOnTheScreen();
    expect(screen.queryByText("Payment sent")).not.toBeOnTheScreen();

    await user.press(screen.getByRole("button", { name: "Cancel or retry" }));
    await user.press(screen.getByRole("button", { name: "Cancel or retry" }));
    expect(await screen.findByRole("button", { name: "PIN" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Face ID" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Fingerprint" })).toBeOnTheScreen();
  });

  it("keeps a failed offline submission editable and safely retryable", async () => {
    const apiClient = {
      getCustomerAccounts: jest.fn().mockResolvedValue({
        accounts: [{ accountId: "acct-safe", displayName: "Everyday account", maskedAccountNumber: "•••• 4321", currency: "ZAR" }],
      }),
      getCustomerBeneficiaries: jest.fn().mockResolvedValue({
        beneficiaries: [{ beneficiaryId: "bene-safe", displayName: "Amina Dlamini", maskedAccountNumber: "•••• 7788", currency: "ZAR", status: "ACTIVE", availableAt: "2026-07-13T00:00:00Z" }],
      }),
      startPayment: jest.fn()
        .mockRejectedValueOnce(new Error("offline"))
        .mockResolvedValueOnce({ paymentReference: "pay_retry_123", state: "AUTHORISATION_REQUIRED", authPolicy: "VOICE_OR_MFA", message: "Payment created" }),
      issueVoiceChallenge: jest.fn().mockResolvedValue({ paymentReference: "pay_retry_123", challengeId: "challenge-retry", phrase: "Confirm payment", expiresAt: "2099-01-01T00:00:00Z", authPolicy: "VOICE_OTP", transactionAmountMinor: 75000, transactionBindingHash: "binding" }),
    } as unknown as VoiceSecureApiClient;
    const user = userEvent.setup();

    await render(<ReadinessDashboard apiClient={apiClient} voiceRecorder={recorder()} />);
    await user.press(screen.getByRole("tab", { name: "Payments" }));
    expect(await screen.findByText("Ready to pay securely.")).toBeOnTheScreen();
    await user.press(screen.getByRole("button", { name: "Review payment" }));
    const submit = screen.getByRole("button", { name: "Try voice demo" });
    await user.press(submit);

    expect(await screen.findByText(/Check your connection and try again/)).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Try voice demo" })).toBeEnabled();

    await user.press(screen.getByRole("button", { name: "Try voice demo" }));
    expect(await screen.findByText(/Listening securely/)).toBeOnTheScreen();
    expect(apiClient.startPayment).toHaveBeenCalledTimes(2);
  });
});

function recorder(): VoiceRecorder {
  return {
    requestPermission: jest.fn().mockResolvedValue(true),
    start: jest.fn().mockResolvedValue(undefined),
    stop: jest.fn().mockResolvedValue({ contentBase64: "YXVkaW8=", codec: "audio/mp4", sampleRateHz: 44100 }),
    cancel: jest.fn().mockResolvedValue(undefined),
  };
}
