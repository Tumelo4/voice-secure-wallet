package com.voicesecure.api;

import java.time.Instant;
import java.util.UUID;

public interface VoiceGatewayClient {
    Challenge issueChallenge(UUID customerId, String phrase, String transactionBindingHash);

    String verify(Verification request);

    record Challenge(UUID challengeId, String phrase, Instant expiresAt) { }

    record Verification(
            UUID customerId,
            UUID challengeId,
            String paymentReference,
            String transactionBindingHash,
            String authPolicy,
            long transactionAmountMinor,
            Instant capturedAt,
            String audioJson,
            String idempotencyKey
    ) { }
}
