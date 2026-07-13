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

test("offline payment queue deduplicates identical customer payment intents", () => {
  const command = paymentCommand({ reference: "Dinner-1" });
  const queuedOnce = enqueueOfflinePayment(createOfflineQueueState(), command, "2026-07-02T10:00:00Z");
  const queuedTwice = enqueueOfflinePayment(queuedOnce, command, "2026-07-02T10:01:00Z");

  assert.equal(queuedTwice.payments.length, 1);
  assert.equal(queuedTwice.payments[0].command.reference, "Dinner-1");
  assert.equal(queuedTwice.payments[0].queuedAt, "2026-07-02T10:00:00Z");
});

test("offline payment queue enforces a local maximum depth", () => {
  const state: OfflinePaymentQueueState = {
    payments: [
      { command: paymentCommand({ reference: "Dinner-1" }), queuedAt: "2026-07-02T10:00:00Z", attempts: 0 },
    ],
    maxDepth: 1,
  };

  assert.throws(
    () => enqueueOfflinePayment(state, paymentCommand({ reference: "Dinner-2" }), "2026-07-02T10:01:00Z"),
    /offline payment queue is full/
  );
});

test("offline drain sends queued payments in order and removes successes", async () => {
  const client = new RecordingPaymentClient();
  const state = enqueueOfflinePayment(
    enqueueOfflinePayment(createOfflineQueueState(), paymentCommand({ reference: "Dinner-1" }), "2026-07-02T10:00:00Z"),
    paymentCommand({ reference: "Dinner-2" }),
    "2026-07-02T10:01:00Z"
  );

  const result = await drainOfflinePaymentQueue(state, { client });

  assert.deepEqual(client.references, ["Dinner-1", "Dinner-2"]);
  assert.equal(result.state.payments.length, 0);
  assert.equal(result.sent, 2);
  assert.equal(result.blocked, false);
});

test("offline drain keeps retryable failures queued and stops behind the blocker", async () => {
  const client = new RecordingPaymentClient({
    failure: new ApiClientError(503, "NETWORK_UNAVAILABLE", "network unavailable"),
  });
  const state = enqueueOfflinePayment(
    enqueueOfflinePayment(createOfflineQueueState(), paymentCommand({ reference: "Dinner-1" }), "2026-07-02T10:00:00Z"),
    paymentCommand({ reference: "Dinner-2" }),
    "2026-07-02T10:01:00Z"
  );

  const result = await drainOfflinePaymentQueue(state, { client });

  assert.deepEqual(client.references, ["Dinner-1"]);
  assert.equal(result.state.payments.length, 2);
  assert.equal(result.state.payments[0].attempts, 1);
  assert.equal(result.retry?.delayMs, 500);
  assert.equal(result.blocked, true);
});

class RecordingPaymentClient {
  readonly references: string[] = [];
  private readonly failure?: unknown;

  constructor(config: { failure?: unknown } = {}) {
    this.failure = config.failure;
  }

  async startPayment(command: StartPaymentCommand): Promise<PaymentStartResult> {
    this.references.push(command.reference);
    if (this.failure) {
      throw this.failure;
    }
    return {
      paymentReference: "VSW-12345678",
      state: "AUTHORISATION_REQUIRED",
      authPolicy: "VOICE_OTP",
      message: "Payment submitted for secure verification.",
    };
  }
}

function paymentCommand(overrides: Partial<StartPaymentCommand> = {}): StartPaymentCommand {
  return {
    sourceAccountId: "wallet-from",
    beneficiaryId: "wallet-to",
    amount: { value: "750.00", currency: "ZAR" },
    reference: "Dinner-1",
    ...overrides,
  };
}
