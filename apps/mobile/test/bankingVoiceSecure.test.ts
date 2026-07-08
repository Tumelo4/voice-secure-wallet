import assert from "node:assert/strict";
import test from "node:test";

import {
  advanceVoiceSecureFlow,
  createTransactionDraft,
  createVoiceSecureFlow,
} from "../src/state/bankingVoiceSecure.ts";

test("voice secure flow starts with a friendly listening prompt", () => {
  const draft = createTransactionDraft();
  const flow = createVoiceSecureFlow(draft);

  assert.equal(flow.stage, "listening");
  assert.equal(flow.prompt, 'Say "Confirm payment" to continue');
  assert.deepEqual(flow.fallbackOptions, ["PIN", "Face ID", "Fingerprint"]);
});

test("voice secure flow moves to payment sent after a successful match", () => {
  const flow = createVoiceSecureFlow(createTransactionDraft());
  const confirmed = advanceVoiceSecureFlow(flow, "match");

  assert.equal(confirmed.stage, "confirmed");
  assert.equal(confirmed.statusLabel, "Payment sent");
  assert.equal(confirmed.message, "Your payment is on its way.");
});

test("voice secure flow uses transaction-specific confirmation copy for top ups", () => {
  const draft = createTransactionDraft("topup");
  const flow = createVoiceSecureFlow(draft);
  const confirmed = advanceVoiceSecureFlow(flow, "match");

  assert.equal(confirmed.stage, "confirmed");
  assert.equal(confirmed.statusLabel, "Top up complete");
  assert.equal(confirmed.message, "Your top up is on its way.");
});

test("voice secure flow reveals fallback after two failed attempts", () => {
  const firstAttempt = advanceVoiceSecureFlow(createVoiceSecureFlow(createTransactionDraft()), "miss");
  const secondAttempt = advanceVoiceSecureFlow(firstAttempt, "miss");

  assert.equal(secondAttempt.stage, "fallback");
  assert.equal(secondAttempt.attempts, 2);
  assert.equal(secondAttempt.message, "We couldn't verify your voice. Try again or use PIN, Face ID, or fingerprint.");
});
