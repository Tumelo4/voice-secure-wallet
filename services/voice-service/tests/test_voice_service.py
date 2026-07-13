from __future__ import annotations

import unittest
from datetime import datetime, timedelta, timezone
from uuid import uuid4

from voice_service import (
    AuthPolicy,
    FallbackMethod,
    InMemoryVoiceRepository,
    VoiceService,
    VoiceServiceError,
    VoiceStatus,
    VoiceVerificationRequest,
    transaction_binding,
)


class VoiceServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository = InMemoryVoiceRepository()
        self.service = VoiceService(self.repository)
        self.user_id = uuid4()
        self.challenge_phrase = "open sesame"
        self.profile = self.service.enroll(
            self.user_id,
            [
                (0.90, 0.20, 0.30, 0.40),
                (0.92, 0.18, 0.29, 0.41),
                (0.91, 0.19, 0.31, 0.39),
            ],
        )
        self.challenge = self.service.issue_challenge(self.user_id, self.challenge_phrase)

    def test_enrollment_requires_three_samples(self) -> None:
        with self.assertRaises(VoiceServiceError):
            self.service.enroll(self.user_id, [(0.1, 0.2), (0.2, 0.3)])

    def test_enrollment_requires_matching_vector_dimensions(self) -> None:
        with self.assertRaisesRegex(VoiceServiceError, "same dimensions"):
            self.service.enroll(self.user_id, [(0.1, 0.2), (0.2,), (0.3, 0.4)])

    def test_challenge_requires_phrase_and_positive_ttl(self) -> None:
        with self.assertRaisesRegex(VoiceServiceError, "phrase is required"):
            self.service.issue_challenge(self.user_id, "   ")
        with self.assertRaisesRegex(VoiceServiceError, "ttl must be positive"):
            self.service.issue_challenge(self.user_id, "open sesame", ttl_seconds=0)

    def test_verified_sample_passes_and_records_fingerprint(self) -> None:
        result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=self.challenge.challenge_id,
                transcript=self.challenge_phrase,
                embedding=(0.91, 0.19, 0.30, 0.40),
                liveness_score=0.96,
                spoof_score=0.05,
                audio_fingerprint_hash="fingerprint-1",
                auth_policy=AuthPolicy.VOICE_ONLY,
                transaction_amount=2500,
                voice_threshold=0.75,
                captured_at=datetime.now(timezone.utc),
            )
        )

        self.assertEqual(VoiceStatus.VERIFIED, result.status)
        self.assertFalse(result.fallback_requested)
        self.assertIsNone(result.fallback_method)
        self.assertEqual(1, len(self.repository.sessions()))

    def test_expired_challenge_requests_fallback(self) -> None:
        expired_capture = self.challenge.expires_at + timedelta(seconds=1)
        result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=self.challenge.challenge_id,
                transcript=self.challenge_phrase,
                embedding=(0.91, 0.19, 0.30, 0.40),
                liveness_score=0.96,
                spoof_score=0.05,
                audio_fingerprint_hash="fingerprint-2",
                auth_policy=AuthPolicy.VOICE_OTP,
                transaction_amount=7500,
                voice_threshold=0.75,
                captured_at=expired_capture,
            )
        )

        self.assertEqual(VoiceStatus.TIMEOUT, result.status)
        self.assertTrue(result.fallback_requested)
        self.assertEqual(FallbackMethod.OTP, result.fallback_method)

    def test_replay_detected_requests_fallback(self) -> None:
        fingerprint = "fingerprint-3"
        first_result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=self.challenge.challenge_id,
                transcript=self.challenge_phrase,
                embedding=(0.91, 0.19, 0.30, 0.40),
                liveness_score=0.96,
                spoof_score=0.05,
                audio_fingerprint_hash=fingerprint,
                auth_policy=AuthPolicy.DEVICE_PIN,
                transaction_amount=2000,
                voice_threshold=0.75,
                captured_at=datetime.now(timezone.utc),
            )
        )
        second_result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=self.challenge.challenge_id,
                transcript=self.challenge_phrase,
                embedding=(0.91, 0.19, 0.30, 0.40),
                liveness_score=0.96,
                spoof_score=0.05,
                audio_fingerprint_hash=fingerprint,
                auth_policy=AuthPolicy.DEVICE_PIN,
                transaction_amount=2000,
                voice_threshold=0.75,
                captured_at=datetime.now(timezone.utc),
            )
        )

        self.assertEqual(VoiceStatus.VERIFIED, first_result.status)
        self.assertEqual(VoiceStatus.SPOOF_DETECTED, second_result.status)
        self.assertEqual(FallbackMethod.PIN, second_result.fallback_method)

    def test_challenge_cannot_be_reused_with_new_fingerprint(self) -> None:
        first_result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=self.challenge.challenge_id,
                transcript=self.challenge_phrase,
                embedding=(0.91, 0.19, 0.30, 0.40),
                liveness_score=0.96,
                spoof_score=0.05,
                audio_fingerprint_hash="fingerprint-3a",
                auth_policy=AuthPolicy.VOICE_OTP,
                transaction_amount=2000,
                voice_threshold=0.75,
                captured_at=datetime.now(timezone.utc),
            )
        )
        second_result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=self.challenge.challenge_id,
                transcript=self.challenge_phrase,
                embedding=(0.91, 0.19, 0.30, 0.40),
                liveness_score=0.96,
                spoof_score=0.05,
                audio_fingerprint_hash="fingerprint-3b",
                auth_policy=AuthPolicy.VOICE_OTP,
                transaction_amount=2000,
                voice_threshold=0.75,
                captured_at=datetime.now(timezone.utc),
            )
        )

        self.assertEqual(VoiceStatus.VERIFIED, first_result.status)
        self.assertEqual(VoiceStatus.SPOOF_DETECTED, second_result.status)
        self.assertEqual("challenge already used", second_result.reason)

    def test_verification_scores_must_be_valid_ranges(self) -> None:
        with self.assertRaises(VoiceServiceError):
            self.service.verify(
                VoiceVerificationRequest(
                    user_id=self.user_id,
                    challenge_id=self.challenge.challenge_id,
                    transcript=self.challenge_phrase,
                    embedding=(0.91, 0.19, 0.30, 0.40),
                    liveness_score=1.20,
                    spoof_score=0.05,
                    audio_fingerprint_hash="fingerprint-range",
                    auth_policy=AuthPolicy.VOICE_ONLY,
                    transaction_amount=2000,
                    voice_threshold=0.75,
                    captured_at=datetime.now(timezone.utc),
                )
            )

        with self.assertRaises(VoiceServiceError):
            self.service.verify(
                VoiceVerificationRequest(
                    user_id=self.user_id,
                    challenge_id=self.challenge.challenge_id,
                    transcript=self.challenge_phrase,
                    embedding=(),
                    liveness_score=0.96,
                    spoof_score=0.05,
                    audio_fingerprint_hash="fingerprint-empty",
                    auth_policy=AuthPolicy.VOICE_ONLY,
                    transaction_amount=2000,
                    voice_threshold=0.75,
                    captured_at=datetime.now(timezone.utc),
                )
            )

    def test_phrase_mismatch_uses_policy_fallback(self) -> None:
        result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=self.challenge.challenge_id,
                transcript="incorrect phrase",
                embedding=(0.80, 0.10, 0.10, 0.10),
                liveness_score=0.85,
                spoof_score=0.10,
                audio_fingerprint_hash="fingerprint-4",
                auth_policy=AuthPolicy.VOICE_OTP,
                transaction_amount=6000,
                voice_threshold=0.75,
                captured_at=datetime.now(timezone.utc),
            )
        )

        self.assertEqual(VoiceStatus.REJECTED, result.status)
        self.assertTrue(result.fallback_requested)
        self.assertEqual(FallbackMethod.OTP, result.fallback_method)

    def test_fallback_method_selection(self) -> None:
        self.assertEqual(FallbackMethod.OTP, self.service.fallback_method_for(AuthPolicy.VOICE_ONLY, 1000))
        self.assertEqual(FallbackMethod.OTP, self.service.fallback_method_for(AuthPolicy.VOICE_OTP, 9000))
        self.assertEqual(FallbackMethod.PIN, self.service.fallback_method_for(AuthPolicy.DEVICE_PIN, 9000))

    def test_voice_challenge_is_bound_to_exact_payment(self) -> None:
        binding = transaction_binding("account-1", "maya", "750", "zar", "Dinner split")
        challenge = self.service.issue_challenge(self.user_id, self.challenge_phrase, transaction_binding_hash=binding)
        result = self.service.verify(
            VoiceVerificationRequest(
                user_id=self.user_id,
                challenge_id=challenge.challenge_id,
                transcript=self.challenge_phrase,
                embedding=(0.91, 0.19, 0.30, 0.40),
                liveness_score=0.96,
                spoof_score=0.05,
                audio_fingerprint_hash="bound-fingerprint",
                auth_policy=AuthPolicy.VOICE_ONLY,
                transaction_amount=75000,
                voice_threshold=0.75,
                captured_at=datetime.now(timezone.utc),
                transaction_binding_hash=transaction_binding("account-1", "maya", "751", "ZAR", "Dinner split"),
            )
        )
        self.assertEqual(VoiceStatus.SPOOF_DETECTED, result.status)
        self.assertEqual("transaction binding mismatch", result.reason)


if __name__ == "__main__":
    unittest.main()
