import assert from "node:assert/strict";
import test from "node:test";

import {
  ApiClientError,
  VoiceSecureApiClient,
  type ApiTransport,
  type ApiTransportRequest,
} from "../src/api/voiceSecureApiClient.ts";
import {
  createIdleRequestState,
  requestFailed,
  requestStarted,
  requestSucceeded,
} from "../src/state/apiRequestModel.ts";

test("startPayment sends auth, trace, idempotency, and JSON body", async () => {
  const transport = new RecordingTransport({
    status: 202,
    body: JSON.stringify({
      sagaId: "saga-1",
      state: "VOICE_VERIFICATION_PENDING",
      traceId: "trace-mobile-1",
      authPolicy: "VOICE_OTP",
      eventCount: 4,
    }),
  });
  const client = new VoiceSecureApiClient({
    token: "token-user-1",
    traceIdFactory: () => "trace-mobile-1",
    transport,
  });

  const result = await client.startPayment({
    sagaId: "saga-1",
    idempotencyKey: "idem-1",
    userId: "user-1",
    fromAccountId: "from-1",
    toAccountId: "to-1",
    amount: 750,
    currency: "ZAR",
  });

  assert.equal(result.state, "VOICE_VERIFICATION_PENDING");
  assert.equal(result.authPolicy, "VOICE_OTP");
  assert.equal(transport.requests[0].method, "POST");
  assert.equal(transport.requests[0].path, "/payments");
  assert.equal(transport.requests[0].headers.Authorization, "Bearer token-user-1");
  assert.equal(transport.requests[0].headers["X-Trace-Id"], "trace-mobile-1");
  assert.equal(transport.requests[0].headers["Idempotency-Key"], "idem-1");
  assert.deepEqual(JSON.parse(transport.requests[0].body ?? "{}"), {
    sagaId: "saga-1",
    userId: "user-1",
    fromAccountId: "from-1",
    toAccountId: "to-1",
    amount: 750,
    currency: "ZAR",
  });
});

test("getWalletBalance maps wallet runtime response", async () => {
  const transport = new RecordingTransport({
    status: 200,
    body: JSON.stringify({
      accountId: "wallet-1",
      currency: "ZAR",
      balance: 1250,
      version: 7,
      updatedAt: "2026-07-02T12:00:00Z",
    }),
  });
  const client = new VoiceSecureApiClient({
    token: "token-user-1",
    traceIdFactory: () => "trace-wallet-1",
    transport,
  });

  const balance = await client.getWalletBalance("wallet-1");

  assert.equal(balance.balance, 1250);
  assert.equal(balance.currency, "ZAR");
  assert.equal(transport.requests[0].method, "GET");
  assert.equal(transport.requests[0].path, "/wallets/wallet-1/balance");
  assert.equal(transport.requests[0].headers.Authorization, "Bearer token-user-1");
  assert.equal(transport.requests[0].headers["X-Trace-Id"], "trace-wallet-1");
});

test("API errors keep status, code, message, and retry hints", async () => {
  const transport = new RecordingTransport({
    status: 429,
    headers: { "Retry-After": "2" },
    body: JSON.stringify({ code: "RATE_LIMITED", message: "rate limit exceeded" }),
  });
  const client = new VoiceSecureApiClient({
    token: "token-user-1",
    traceIdFactory: () => "trace-rate-1",
    transport,
  });

  await assert.rejects(
    () => client.getWalletBalance("wallet-1"),
    (error) => {
      assert.ok(error instanceof ApiClientError);
      assert.equal(error.status, 429);
      assert.equal(error.code, "RATE_LIMITED");
      assert.equal(error.retryAfter, "2");
      assert.equal(error.message, "rate limit exceeded");
      return true;
    }
  );
});

test("request state model supports Redux-friendly async transitions", () => {
  const idle = createIdleRequestState<{ balance: number }>();
  const loading = requestStarted(idle, "trace-state-1");
  const succeeded = requestSucceeded(loading, { balance: 1250 });
  const failed = requestFailed(loading, { status: 403, code: "FORBIDDEN", message: "bearer token is invalid" });

  assert.equal(idle.status, "idle");
  assert.equal(loading.status, "loading");
  assert.equal(loading.traceId, "trace-state-1");
  assert.equal(succeeded.status, "succeeded");
  assert.equal(succeeded.data?.balance, 1250);
  assert.equal(failed.status, "failed");
  assert.equal(failed.error?.code, "FORBIDDEN");
});

class RecordingTransport implements ApiTransport {
  readonly requests: ApiTransportRequest[] = [];
  private readonly response: { status: number; body: string; headers?: Record<string, string> };

  constructor(response: { status: number; body: string; headers?: Record<string, string> }) {
    this.response = response;
  }

  async send(request: ApiTransportRequest) {
    this.requests.push(request);
    return this.response;
  }
}
