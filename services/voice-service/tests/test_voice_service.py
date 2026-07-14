from __future__ import annotations

import unittest
from dataclasses import fields
from datetime import datetime, timedelta, timezone
from uuid import uuid4

from voice_service import (
    AuthPolicy,
    FallbackMethod,
    InMemoryVoiceRepository,
    RawAudioSample,
    ServerSideVoiceInferenceAdapter,
    VoiceInference,
    VoiceService,
    VoiceServiceError,
    VoiceStatus,
    VoiceVerificationRequest,
    transaction_binding,
)
from voice_service import _average_vectors, _cosine_similarity


class FakeInference:
    def __init__(self) -> None:
        self.results: dict[bytes, VoiceInference] = {}

    def add(self, audio: RawAudioSample, *, transcript="open sesame", embedding=(0.91, 0.19, 0.30, 0.40),
            liveness=0.96, spoof=0.05, fingerprint=None) -> None:
        self.results[audio.content] = VoiceInference(
            transcript, embedding, liveness, spoof, fingerprint or audio.content.hex())

    def infer(self, sample: RawAudioSample) -> VoiceInference:
        return self.results[sample.content]


def audio(value: str) -> RawAudioSample:
    return RawAudioSample(value.encode(), "audio/wav", 16000)


class VoiceServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository = InMemoryVoiceRepository()
        self.inference = FakeInference()
        self.enrollment = [audio("enroll-1"), audio("enroll-2"), audio("enroll-3")]
        for sample in self.enrollment:
            self.inference.add(sample)
        self.service = VoiceService(self.repository, self.inference)
        self.user_id = uuid4()
        self.service.enroll(self.user_id, self.enrollment)
        self.challenge = self.service.issue_challenge(self.user_id, "open sesame")

    def request(self, sample: RawAudioSample, **overrides) -> VoiceVerificationRequest:
        values = {
            "user_id": self.user_id,
            "challenge_id": self.challenge.challenge_id,
            "audio": sample,
            "auth_policy": AuthPolicy.VOICE_ONLY,
            "transaction_amount": 2500,
            "captured_at": datetime.now(timezone.utc),
            "transaction_binding_hash": "",
        }
        values.update(overrides)
        return VoiceVerificationRequest(**values)

    def test_client_cannot_supply_embedding_or_security_scores(self) -> None:
        names = {field.name for field in fields(VoiceVerificationRequest)}
        self.assertTrue({"embedding", "liveness_score", "spoof_score", "audio_fingerprint_hash"}.isdisjoint(names))
        with self.assertRaises(TypeError):
            VoiceVerificationRequest(  # type: ignore[call-arg]
                user_id=self.user_id, challenge_id=self.challenge.challenge_id, audio=audio("forged"),
                auth_policy=AuthPolicy.VOICE_ONLY, transaction_amount=1, captured_at=datetime.now(timezone.utc),
                liveness_score=1.0,
            )

    def test_server_inference_verifies_and_records_fingerprint(self) -> None:
        sample = audio("fresh-live")
        self.inference.add(sample, fingerprint="server-owned-fingerprint")
        result = self.service.verify(self.request(sample))
        self.assertEqual(VoiceStatus.VERIFIED, result.status)
        self.assertTrue(self.repository.fingerprint_seen(self.user_id, "server-owned-fingerprint"))

    def test_server_computed_spoof_signal_rejects_synthetic_audio(self) -> None:
        sample = audio("synthetic-replay")
        self.inference.add(sample, liveness=0.1, spoof=0.99)
        result = self.service.verify(self.request(sample))
        self.assertEqual(VoiceStatus.SPOOF_DETECTED, result.status)
        self.assertEqual("spoof detection triggered", result.reason)

    def test_raw_audio_replay_is_rejected_from_server_fingerprint(self) -> None:
        first = audio("same-recording")
        self.inference.add(first, fingerprint="same-hash")
        self.assertEqual(VoiceStatus.VERIFIED, self.service.verify(self.request(first)).status)
        next_challenge = self.service.issue_challenge(self.user_id, "open sesame")
        replay = self.request(first, challenge_id=next_challenge.challenge_id)
        self.assertEqual(VoiceStatus.SPOOF_DETECTED, self.service.verify(replay).status)

    def test_expired_and_reused_challenges_request_fallback(self) -> None:
        sample = audio("expired")
        self.inference.add(sample)
        expired = self.request(sample, captured_at=self.challenge.expires_at + timedelta(seconds=1),
                               auth_policy=AuthPolicy.VOICE_OTP, transaction_amount=7500)
        result = self.service.verify(expired)
        self.assertEqual(VoiceStatus.TIMEOUT, result.status)
        self.assertEqual(FallbackMethod.OTP, result.fallback_method)

    def test_transaction_binding_and_phrase_are_server_checked(self) -> None:
        binding = transaction_binding("account-1", "maya", "750", "zar", "Dinner split")
        challenge = self.service.issue_challenge(self.user_id, "open sesame", transaction_binding_hash=binding)
        sample = audio("bound")
        self.inference.add(sample, transcript="attacker phrase")
        mismatch = self.request(sample, challenge_id=challenge.challenge_id, transaction_binding_hash="forged")
        result = self.service.verify(mismatch)
        self.assertEqual(VoiceStatus.SPOOF_DETECTED, result.status)

    def test_enrollment_and_audio_validation(self) -> None:
        with self.assertRaises(VoiceServiceError):
            self.service.enroll(self.user_id, self.enrollment[:2])
        with self.assertRaisesRegex(VoiceServiceError, "sample rate"):
            ServerSideVoiceInferenceAdapter().infer(RawAudioSample(b"audio", "audio/wav", 1))

    def test_interim_adapter_rejects_repetitive_synthetic_fixture(self) -> None:
        adapter = ServerSideVoiceInferenceAdapter()
        inference = adapter.infer(RawAudioSample(b"open sesame\n" + (b"A" * 512), "audio/x-voicesecure-test", 16000))
        self.assertGreaterEqual(inference.spoof_score, 0.8)
        self.assertLess(inference.liveness_score, 0.35)

    def test_missing_profile_challenge_and_wrong_owner_are_rejected(self) -> None:
        sample = audio("boundary-rejections")
        self.inference.add(sample)
        unknown_user = uuid4()
        missing_profile = self.service.verify(self.request(sample, user_id=unknown_user))
        self.assertEqual("voice profile not enrolled", missing_profile.reason)

        missing_challenge = self.service.verify(self.request(sample, challenge_id=uuid4()))
        self.assertEqual("challenge not found", missing_challenge.reason)

        other_user = uuid4()
        self.service.enroll(other_user, self.enrollment)
        wrong_owner = self.service.verify(self.request(sample, user_id=other_user))
        self.assertEqual("challenge does not belong to user", wrong_owner.reason)

    def test_phrase_confidence_and_single_use_controls_reject(self) -> None:
        phrase_sample = audio("wrong-phrase")
        self.inference.add(phrase_sample, transcript="wrong words")
        self.assertEqual("challenge phrase mismatch", self.service.verify(self.request(phrase_sample)).reason)

        challenge = self.service.issue_challenge(self.user_id, "open sesame")
        weak_sample = audio("weak-speaker")
        self.inference.add(weak_sample, embedding=(0.0, 1.0, 0.0, 0.0), liveness=0.4, spoof=0.5)
        weak = self.request(weak_sample, challenge_id=challenge.challenge_id)
        self.assertEqual("voice confidence below threshold", self.service.verify(weak).reason)
        self.assertEqual("challenge already used", self.service.verify(weak).reason)

    def test_invalid_inputs_and_vector_shapes_fail_closed(self) -> None:
        with self.assertRaisesRegex(VoiceServiceError, "phrase"):
            self.service.issue_challenge(self.user_id, "  ")
        with self.assertRaisesRegex(VoiceServiceError, "ttl"):
            self.service.issue_challenge(self.user_id, "phrase", ttl_seconds=0)
        with self.assertRaisesRegex(VoiceServiceError, "payment amount"):
            transaction_binding("source", "beneficiary", "not-money", "ZAR", "reference")
        with self.assertRaisesRegex(VoiceServiceError, "fields"):
            transaction_binding("", "beneficiary", "1", "ZAR", "reference")
        with self.assertRaisesRegex(VoiceServiceError, "same dimensions"):
            _average_vectors(((1.0,), (1.0, 2.0)))
        with self.assertRaisesRegex(VoiceServiceError, "dimensions"):
            _cosine_similarity((1.0,), (1.0, 2.0))
        self.assertEqual(0.0, _cosine_similarity((0.0, 0.0), (1.0, 1.0)))

        for invalid in (
            RawAudioSample(b"", "audio/wav", 16000),
            RawAudioSample(b"audio", " ", 16000),
        ):
            with self.assertRaises(VoiceServiceError):
                self.service.verify(self.request(invalid))
        with self.assertRaisesRegex(VoiceServiceError, "negative"):
            self.service.verify(self.request(audio("negative"), transaction_amount=-1))


if __name__ == "__main__":
    unittest.main()
