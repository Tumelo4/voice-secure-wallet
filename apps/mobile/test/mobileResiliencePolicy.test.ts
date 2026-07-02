import assert from "node:assert/strict";
import test from "node:test";

import { ApiClientError, type PaymentStartResult, type StartPaymentCommand } from "../src/api/voiceSecureApiClient.ts";
import {
  defaultRetryPolicy,
  createOfflineQueueState,
  decideRetry,
  drainOfflinePaymentQueue,
  enqueueOfflinePayment,
  type OfflinePaymentQueueState,
} from "../src/state/mobileResiliencePolicy.ts";

test("retry policy backs off retryable transport failures with a cap", () => {
  const first = decideRetry({ status: 503, code: "NETWORK_UNAVAILABLE", message: "network unavailable" }, 1);
  const third = decideRetry({ status: 503, code: "NETWORK_UNAVAILABLE", message: "network unavailable" }, 3);
  const sixth = decideRetry(
    { status: 503, code: "NETWORK_UNAVAILABLE", message: "network unavailable" },
    6,
    { ...defaultRetryPolicy, maxAttempts: 10 }
  );

  assert.equal(first.shouldRetry, true);
  assert.equal(first.delayMs, 500);
  assert.equal(third.delayMs, 2_000);
  assert.equal(sixth.delayMs, 8_000);
});

test("retry policy does not retry auth, validation, or exhausted attempts", () => {
  assert.equal(decideRetry({ status: 401, code: "AUTH_SESSION_REQUIRED", message: "login required" }, 1).shouldRetry, false);
  assert.equal(decideRetry({ status: 400, code: "VALIDATION_FAILED", message: "amount is required" }, 1).shouldRetry, false);
  assert.equal(decideRetry({ status: 503, code: "NETWORK_UNAVAILABLE", message: "network unavailable" }, 4).shouldRetry, false);
});

test("offline payment queue enqueues idempotently by idempotency key", () => {
  const command = paymentCommand({ idempotencyKey: "idem-1" });
  const queuedOnce = enqueueOfflinePayment(createOfflineQueueState(), command, "2026-07-02T10:00:00Z");
  const queuedTwice = enqueueOfflinePayment(queuedOnce, command, "2026-07-02T10:01:00Z");

  assert.equal(queuedTwice.payments.length, 1);
  assert.equal(queuedTwice.payments[0].command.idempotencyKey, "idem-1");
  assert.equal(queuedTwice.payments[0].queuedAt, "2026-07-02T10:00:00Z");
});

test("offline payment queue enforces a local maximum depth", () => {
  const state: OfflinePaymentQueueState = {
    payments: [
      { command: paymentCommand({ idempotencyKey: "idem-1" }), queuedAt: "2026-07-02T10:00:00Z", attempts: 0 },
    ],
    maxDepth: 1,
  };

  assert.throws(
    () => enqueueOfflinePayment(state, paymentCommand({ idempotencyKey: "idem-2" }), "2026-07-02T10:01:00Z"),
    /offline payment queue is full/
  );
});

test("offline drain sends queued payments in order and removes successes", async () => {
  const client = new RecordingPaymentClient();
  const state = enqueueOfflinePayment(
    enqueueOfflinePayment(createOfflineQueueState(), paymentCommand({ sagaId: "saga-1", idempotencyKey: "idem-1" }), "2026-07-02T10:00:00Z"),
    paymentCommand({ sagaId: "saga-2", idempotencyKey: "idem-2" }),
    "2026-07-02T10:01:00Z"
  );

  const result = await drainOfflinePaymentQueue(state, { client });

  assert.deepEqual(client.sagaIds, ["saga-1", "saga-2"]);
  assert.equal(result.state.payments.length, 0);
  assert.equal(result.sent, 2);
  assert.equal(result.blocked, false);
});

test("offline drain keeps retryable failures queued and stops behind the blocker", async () => {
  const client = new RecordingPaymentClient({
    failure: new ApiClientError(503, "NETWORK_UNAVAILABLE", "network unavailable"),
  });
  const state = enqueueOfflinePayment(
    enqueueOfflinePayment(createOfflineQueueState(), paymentCommand({ sagaId: "saga-1", idempotencyKey: "idem-1" }), "2026-07-02T10:00:00Z"),
    paymentCommand({ sagaId: "saga-2", idempotencyKey: "idem-2" }),
    "2026-07-02T10:01:00Z"
  );

  const result = await drainOfflinePaymentQueue(state, { client });

  assert.deepEqual(client.sagaIds, ["saga-1"]);
  assert.equal(result.state.payments.length, 2);
  assert.equal(result.state.payments[0].attempts, 1);
  assert.equal(result.retry?.delayMs, 500);
  assert.equal(result.blocked, true);
});

class RecordingPaymentClient {
  readonly sagaIds: string[] = [];
  private readonly failure?: unknown;

  constructor(config: { failure?: unknown } = {}) {
    this.failure = config.failure;
  }

  async startPayment(command: StartPaymentCommand): Promise<PaymentStartResult> {
    this.sagaIds.push(command.sagaId);
    if (this.failure) {
      throw this.failure;
    }
    return {
      sagaId: command.sagaId,
      state: "VOICE_VERIFICATION_PENDING",
      traceId: "trace-offline-drain-1",
      authPolicy: "VOICE_OTP",
      eventCount: 4,
    };
  }
}

function paymentCommand(overrides: Partial<StartPaymentCommand> = {}): StartPaymentCommand {
  return {
    sagaId: "saga-1",
    idempotencyKey: "idem-1",
    userId: "user-1",
    fromAccountId: "wallet-from",
    toAccountId: "wallet-to",
    amount: 750,
    currency: "ZAR",
    ...overrides,
  };
}
