import assert from "node:assert/strict";
import test from "node:test";
import { ApiClientError, VoiceSecureApiClient, type ApiTransport, type ApiTransportResponse, type VoiceChallengeResult } from "../src/api/voiceSecureApiClient.ts";
import { VoiceCaptureSession, type VoiceRecorder } from "../src/voice/voiceCaptureSession.ts";

const challenge: VoiceChallengeResult = {
  paymentReference: "VSW-42",
  challengeId: "challenge-42",
  phrase: "Confirm payment",
  expiresAt: "2026-07-16T12:01:00.000Z",
  authPolicy: "VOICE_OTP",
  transactionAmountMinor: 4200,
  transactionBindingHash: "binding-42",
};

test("capture requests permission, records, and uploads challenge-bound audio", async () => {
  const recorder = new FakeRecorder();
  const transport = new QueueTransport([{ status: 200, body: JSON.stringify({ verificationId: "v-1", status: "VERIFIED", fallbackRequested: false, reason: "accepted" }) }]);
  const session = new VoiceCaptureSession(client(transport), recorder, challenge, () => new Date("2026-07-16T12:00:10Z"));

  await session.begin();
  assert.equal(session.state.status, "recording");
  await session.submit();

  assert.deepEqual(recorder.calls, ["permission", "start", "stop"]);
  assert.equal(session.state.status, "completed");
  const request = transport.requests[0];
  assert.equal(request.path, "/v1/voice/challenges/challenge-42/verification");
  const body = JSON.parse(request.body ?? "{}");
  assert.equal(body.paymentReference, "VSW-42");
  assert.equal(body.transactionBindingHash, "binding-42");
  assert.equal(body.audio.contentBase64, "YXVkaW8=");
});

test("permission denial and cancellation fail closed without upload", async () => {
  const denied = new FakeRecorder(false);
  const transport = new QueueTransport([]);
  const deniedSession = new VoiceCaptureSession(client(transport), denied, challenge, () => new Date("2026-07-16T12:00:10Z"));
  await deniedSession.begin();
  assert.equal(deniedSession.state.status, "failed");

  const recorder = new FakeRecorder();
  const cancelled = new VoiceCaptureSession(client(transport), recorder, challenge, () => new Date("2026-07-16T12:00:10Z"));
  await cancelled.begin();
  await cancelled.cancel();
  assert.equal(cancelled.state.status, "cancelled");
  assert.equal(transport.requests.length, 0);
});

test("retry reuses captured audio and idempotency key only for transient failures", async () => {
  const recorder = new FakeRecorder();
  const transport = new QueueTransport([
    { status: 503, body: JSON.stringify({ code: "UNAVAILABLE", message: "try again" }) },
    { status: 200, body: JSON.stringify({ verificationId: "v-2", status: "VERIFIED", fallbackRequested: false, reason: "accepted" }) },
  ]);
  const session = new VoiceCaptureSession(client(transport), recorder, challenge, () => new Date("2026-07-16T12:00:10Z"));
  await session.begin();
  await session.submit();
  assert.equal(session.state.status, "retryable");
  await session.submit();
  assert.equal(session.state.status, "completed");
  assert.deepEqual(recorder.calls, ["permission", "start", "stop"]);
  assert.equal(transport.requests[0].headers["Idempotency-Key"], transport.requests[1].headers["Idempotency-Key"]);
});

class FakeRecorder implements VoiceRecorder {
  calls: string[] = [];
  private readonly permitted: boolean;
  constructor(permitted = true) { this.permitted = permitted; }
  async requestPermission() { this.calls.push("permission"); return this.permitted; }
  async start() { this.calls.push("start"); }
  async stop() { this.calls.push("stop"); return { contentBase64: "YXVkaW8=", codec: "audio/mp4", sampleRateHz: 44100 }; }
  async cancel() { this.calls.push("cancel"); }
}

class QueueTransport implements ApiTransport {
  requests: Parameters<ApiTransport["send"]>[0][] = [];
  private readonly responses: ApiTransportResponse[];
  constructor(responses: ApiTransportResponse[]) { this.responses = responses; }
  async send(request: Parameters<ApiTransport["send"]>[0]) {
    this.requests.push(request);
    const response = this.responses.shift();
    if (!response) throw new ApiClientError(500, "NO_RESPONSE", "missing response");
    return response;
  }
}

function client(transport: ApiTransport) {
  return new VoiceSecureApiClient({ token: "token", traceIdFactory: () => "trace", transport });
}
