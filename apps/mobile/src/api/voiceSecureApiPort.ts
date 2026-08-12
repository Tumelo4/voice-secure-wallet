import type {
  BeneficiarySummary,
  CustomerAccountsResult,
  CustomerBeneficiariesResult,
  PaymentStartResult,
  StartPaymentCommand,
  VoiceChallengeCommand,
  VoiceChallengeResult,
  VoiceVerificationCommand,
  VoiceVerificationResult,
} from "./voiceSecureApiClient.ts";

export interface CustomerPaymentApi {
  getCustomerAccounts(): Promise<CustomerAccountsResult>;
  getCustomerBeneficiaries(): Promise<CustomerBeneficiariesResult>;
  startPayment(command: StartPaymentCommand): Promise<PaymentStartResult>;
  getPaymentStatus(paymentReference: string): Promise<PaymentStartResult>;
  issueVoiceChallenge(command: VoiceChallengeCommand): Promise<VoiceChallengeResult>;
}

export interface VoiceVerificationApi {
  verifyVoice(command: VoiceVerificationCommand): Promise<VoiceVerificationResult>;
}

export type PaymentJourneyApi = CustomerPaymentApi & VoiceVerificationApi;

export type { BeneficiarySummary };
