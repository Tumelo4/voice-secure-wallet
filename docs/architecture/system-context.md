# Target system context

VoiceSecure Wallet serves authenticated customers and authorised operations staff. The mobile app calls one modular Java application. The Java application owns identity integration, accounts, beneficiaries, payments, ledger, fraud, compliance, notification, support and reconciliation boundaries. A separately deployable voice-verification runtime returns bounded biometric decisions. Banks/payment providers and identity providers are external systems.

Financial truth remains in the append-only double-entry ledger. Provider truth is reconciled against it; neither mobile state nor voice output can change balances directly.

