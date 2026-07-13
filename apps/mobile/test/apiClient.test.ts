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

test("startPayment sends customer intent and a hidden idempotency header", async () => {
  const transport = new RecordingTransport({
    status: 202,
    body: JSON.stringify({
      paymentReference: "VSW-12345678",
      state: "AUTHORISATION_REQUIRED",
      authPolicy: "VOICE_OTP",
      message: "Payment submitted for secure verification.",
    }),
  });
  const client = new VoiceSecureApiClient({
    token: "token-user-1",
    traceIdFactory: () => "trace-mobile-1",
    transport,
  });

  const result = await client.startPayment({
    sourceAccountId: "from-1",
    beneficiaryId: "to-1",
    amount: { value: "750.00", currency: "ZAR" },
    reference: "Dinner split",
  });

  assert.equal(result.state, "AUTHORISATION_REQUIRED");
  assert.equal(result.authPolicy, "VOICE_OTP");
  assert.equal(transport.requests[0].method, "POST");
  assert.equal(transport.requests[0].path, "/v1/payments");
  assert.equal(transport.requests[0].headers.Authorization, "Bearer token-user-1");
  assert.equal(transport.requests[0].headers["X-Trace-Id"], "trace-mobile-1");
  assert.match(transport.requests[0].headers["Idempotency-Key"] ?? "", /^[0-9a-f-]{36}$/i);
  assert.deepEqual(JSON.parse(transport.requests[0].body ?? "{}"), {
    sourceAccountId: "from-1",
    beneficiaryId: "to-1",
    amount: { value: "750.00", currency: "ZAR" },
    reference: "Dinner split",
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

test("getPaymentStatus reads only a customer-safe payment reference", async () => {
  const transport = new RecordingTransport({
    status: 200,
    body: JSON.stringify({ paymentReference: "VSW-safe_reference", state: "PROCESSING", authPolicy: "VOICE_OTP", message: "Processing." }),
  });
  const client = new VoiceSecureApiClient({ token: "token-user-1", traceIdFactory: () => "trace-status", transport });

  const result = await client.getPaymentStatus("VSW-safe_reference");

  assert.equal(result.state, "PROCESSING");
  assert.equal(transport.requests[0].method, "GET");
  assert.equal(transport.requests[0].path, "/v1/payments/VSW-safe_reference");
});

test("getCustomerAccounts loads only the authenticated customer's selectable accounts", async () => {
  const transport = new RecordingTransport({
    status: 200,
    body: JSON.stringify({
      accounts: [{ accountId: "acc-1", displayName: "Everyday", maskedAccountNumber: "•••• 5124", currency: "ZAR" }],
    }),
  });
  const client = new VoiceSecureApiClient({
    token: "token-user-1",
    traceIdFactory: () => "trace-accounts-1",
    transport,
  });

  const result = await client.getCustomerAccounts();

  assert.equal(result.accounts[0].maskedAccountNumber, "•••• 5124");
  assert.equal(transport.requests[0].path, "/v1/me/accounts");
  assert.equal(transport.requests[0].headers.Authorization, "Bearer token-user-1");
});

test("beneficiary APIs use customer-safe identifiers and masked details", async () => {
  const body = JSON.stringify({
    beneficiaryId: "ben-1", displayName: "Maya", maskedAccountNumber: "•••• 9012",
    currency: "ZAR", status: "COOLING_OFF", availableAt: "2026-07-14T12:00:00Z",
  });
  const transport = new RecordingTransport({ status: 201, body });
  const client = new VoiceSecureApiClient({ token: "token-user-1", traceIdFactory: () => "trace-beneficiary", transport });

  const created = await client.createBeneficiary({ displayName: "Maya", bankCode: "NED", accountNumber: "123456789012" });

  assert.equal(created.beneficiaryId, "ben-1");
  assert.equal(transport.requests[0].path, "/v1/me/beneficiaries");
  assert.deepEqual(JSON.parse(transport.requests[0].body ?? "{}"), {
    displayName: "Maya", bankCode: "NED", accountNumber: "123456789012",
  });
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
