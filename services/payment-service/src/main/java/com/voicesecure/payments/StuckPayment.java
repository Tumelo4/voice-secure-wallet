package com.voicesecure.payments;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record StuckPayment(UUID sagaId, PaymentSagaState state, Instant updatedAt, Duration age, String action) { }
