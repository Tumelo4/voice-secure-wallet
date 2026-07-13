from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from hashlib import sha256
from math import sqrt
from decimal import Decimal, InvalidOperation
from typing import Iterable, Optional, Protocol, Sequence
from uuid import UUID, uuid4


class VoiceStatus(str, Enum):
    VERIFIED = "VERIFIED"
    REJECTED = "REJECTED"
    TIMEOUT = "TIMEOUT"
    SPOOF_DETECTED = "SPOOF_DETECTED"


class FallbackMethod(str, Enum):
    OTP = "OTP"
    PIN = "PIN"
    HARDWARE_KEY = "HARDWARE_KEY"


class AuthPolicy(str, Enum):
    VOICE_ONLY = "VOICE_ONLY"
    VOICE_OTP = "VOICE_OTP"
    DEVICE_PIN = "DEVICE_PIN"


@dataclass(frozen=True)
class VoiceChallenge:
    challenge_id: UUID
    user_id: UUID
    phrase: str
    issued_at: datetime
    expires_at: datetime
    transaction_binding_hash: str = ""


@dataclass(frozen=True)
class VoiceProfile:
    user_id: UUID
    embedding: tuple[float, ...]
    enrolled_at: datetime
    sample_count: int


@dataclass(frozen=True)
class VoiceVerificationRequest:
    user_id: UUID
    challenge_id: UUID
    transcript: str
    embedding: tuple[float, ...]
    liveness_score: float
    spoof_score: float
    audio_fingerprint_hash: str
    auth_policy: AuthPolicy
    transaction_amount: int
    voice_threshold: float
    captured_at: datetime
    transaction_binding_hash: str = ""


@dataclass(frozen=True)
class VoiceVerificationResult:
    verification_id: UUID
    user_id: UUID
    challenge_id: UUID
    status: VoiceStatus
    confidence: float
    fallback_requested: bool
    fallback_method: Optional[FallbackMethod]
    reason: str
    verified_at: datetime


class VoiceServiceError(RuntimeError):
    pass


class VoiceRepository(Protocol):
    def save_profile(self, profile: VoiceProfile) -> None:
        ...

    def get_profile(self, user_id: UUID) -> Optional[VoiceProfile]:
        ...

    def save_challenge(self, challenge: VoiceChallenge) -> None:
        ...

    def get_challenge(self, challenge_id: UUID) -> Optional[VoiceChallenge]:
        ...

    def mark_challenge_attempted(self, challenge_id: UUID) -> None:
        ...

    def challenge_attempted(self, challenge_id: UUID) -> bool:
        ...

    def remember_fingerprint(self, user_id: UUID, audio_fingerprint_hash: str) -> None:
        ...

    def fingerprint_seen(self, user_id: UUID, audio_fingerprint_hash: str) -> bool:
        ...

    def save_session(self, result: VoiceVerificationResult) -> None:
        ...

    def sessions(self) -> list[VoiceVerificationResult]:
        ...


class InMemoryVoiceRepository:
    def __init__(self) -> None:
        self._profiles: dict[UUID, VoiceProfile] = {}
        self._challenges: dict[UUID, VoiceChallenge] = {}
        self._attempted_challenges: set[UUID] = set()
        self._audio_fingerprints: dict[UUID, set[str]] = {}
        self._verification_sessions: list[VoiceVerificationResult] = []

    def save_profile(self, profile: VoiceProfile) -> None:
        self._profiles[profile.user_id] = profile

    def get_profile(self, user_id: UUID) -> Optional[VoiceProfile]:
        return self._profiles.get(user_id)

    def save_challenge(self, challenge: VoiceChallenge) -> None:
        self._challenges[challenge.challenge_id] = challenge

    def get_challenge(self, challenge_id: UUID) -> Optional[VoiceChallenge]:
        return self._challenges.get(challenge_id)

    def mark_challenge_attempted(self, challenge_id: UUID) -> None:
        self._attempted_challenges.add(challenge_id)

    def challenge_attempted(self, challenge_id: UUID) -> bool:
        return challenge_id in self._attempted_challenges

    def remember_fingerprint(self, user_id: UUID, audio_fingerprint_hash: str) -> None:
        self._audio_fingerprints.setdefault(user_id, set()).add(audio_fingerprint_hash)

    def fingerprint_seen(self, user_id: UUID, audio_fingerprint_hash: str) -> bool:
        return audio_fingerprint_hash in self._audio_fingerprints.get(user_id, set())

    def save_session(self, result: VoiceVerificationResult) -> None:
        self._verification_sessions.append(result)

    def sessions(self) -> list[VoiceVerificationResult]:
        return list(self._verification_sessions)


class VoiceService:
    def __init__(self, repository: VoiceRepository) -> None:
        self._repository = repository

    def enroll(self, user_id: UUID, samples: Sequence[Sequence[float]]) -> VoiceProfile:
        if len(samples) != 3:
            raise VoiceServiceError("voice enrollment requires exactly 3 samples")
        vectors = [tuple(float(value) for value in sample) for sample in samples]
        for vector in vectors:
            _validate_embedding(vector, "enrollment sample")
        embedding = _average_vectors(vectors)
        profile = VoiceProfile(
            user_id=user_id,
            embedding=embedding,
            enrolled_at=datetime.now(timezone.utc),
            sample_count=3,
        )
        self._repository.save_profile(profile)
        return profile

    def issue_challenge(
        self,
        user_id: UUID,
        phrase: str,
        ttl_seconds: int = 30,
        transaction_binding_hash: str = "",
    ) -> VoiceChallenge:
        if not phrase.strip():
            raise VoiceServiceError("challenge phrase is required")
        if ttl_seconds <= 0:
            raise VoiceServiceError("challenge ttl must be positive")
        issued_at = datetime.now(timezone.utc)
        challenge = VoiceChallenge(
            challenge_id=uuid4(),
            user_id=user_id,
            phrase=phrase.strip(),
            issued_at=issued_at,
            expires_at=issued_at + timedelta(seconds=ttl_seconds),
            transaction_binding_hash=transaction_binding_hash.strip(),
        )
        self._repository.save_challenge(challenge)
        return challenge

    def verify(self, request: VoiceVerificationRequest) -> VoiceVerificationResult:
        _validate_verification_request(request)
        profile = self._repository.get_profile(request.user_id)
        if profile is None:
            return self._reject(request, "voice profile not enrolled", VoiceStatus.REJECTED)

        challenge = self._repository.get_challenge(request.challenge_id)
        if challenge is None:
            return self._reject(request, "challenge not found", VoiceStatus.REJECTED)

        if request.captured_at > challenge.expires_at:
            return self._reject(request, "challenge expired", VoiceStatus.TIMEOUT)

        if challenge.user_id != request.user_id:
            return self._reject(request, "challenge does not belong to user", VoiceStatus.REJECTED)

        if challenge.transaction_binding_hash and not _constant_time_equal(
            challenge.transaction_binding_hash,
            request.transaction_binding_hash,
        ):
            return self._reject(request, "transaction binding mismatch", VoiceStatus.SPOOF_DETECTED)

        if self._repository.challenge_attempted(request.challenge_id):
            return self._reject(request, "challenge already used", VoiceStatus.SPOOF_DETECTED)
        self._repository.mark_challenge_attempted(request.challenge_id)

        if self._repository.fingerprint_seen(request.user_id, request.audio_fingerprint_hash):
            return self._reject(request, "audio fingerprint replay detected", VoiceStatus.SPOOF_DETECTED)

        normalized_transcript = _normalize(request.transcript)
        if normalized_transcript != _normalize(challenge.phrase):
            return self._reject(request, "challenge phrase mismatch", VoiceStatus.REJECTED)

        similarity = _cosine_similarity(profile.embedding, request.embedding)
        confidence = _confidence(similarity, request.liveness_score, request.spoof_score)

        if request.spoof_score >= 0.8 or request.liveness_score < 0.35:
            return self._reject(request, "spoof detection triggered", VoiceStatus.SPOOF_DETECTED, confidence)

        if confidence < request.voice_threshold:
            return self._reject(request, "voice confidence below threshold", VoiceStatus.REJECTED, confidence)

        self._repository.remember_fingerprint(request.user_id, request.audio_fingerprint_hash)
        result = VoiceVerificationResult(
            verification_id=uuid4(),
            user_id=request.user_id,
            challenge_id=request.challenge_id,
            status=VoiceStatus.VERIFIED,
            confidence=confidence,
            fallback_requested=False,
            fallback_method=None,
            reason="verified",
            verified_at=datetime.now(timezone.utc),
        )
        self._repository.save_session(result)
        return result

    def fallback_method_for(self, auth_policy: AuthPolicy, transaction_amount: int) -> FallbackMethod:
        if auth_policy == AuthPolicy.DEVICE_PIN:
            return FallbackMethod.PIN
        if transaction_amount > 5000 and auth_policy == AuthPolicy.VOICE_OTP:
            return FallbackMethod.OTP
        return FallbackMethod.OTP

    def _reject(
        self,
        request: VoiceVerificationRequest,
        reason: str,
        status: VoiceStatus,
        confidence: float = 0.0,
    ) -> VoiceVerificationResult:
        result = VoiceVerificationResult(
            verification_id=uuid4(),
            user_id=request.user_id,
            challenge_id=request.challenge_id,
            status=status,
            confidence=round(confidence, 3),
            fallback_requested=True,
            fallback_method=self.fallback_method_for(request.auth_policy, request.transaction_amount),
            reason=reason,
            verified_at=datetime.now(timezone.utc),
        )
        self._repository.save_session(result)
        return result


def _average_vectors(vectors: Sequence[Sequence[float]]) -> tuple[float, ...]:
    dimensions = len(vectors[0])
    if any(len(vector) != dimensions for vector in vectors):
        raise VoiceServiceError("all enrollment vectors must have the same dimensions")
    return tuple(sum(values) / len(vectors) for values in zip(*vectors))


def _cosine_similarity(left: Iterable[float], right: Iterable[float]) -> float:
    left_values = tuple(float(value) for value in left)
    right_values = tuple(float(value) for value in right)
    if len(left_values) != len(right_values):
        raise VoiceServiceError("embedding dimensions must match")
    dot = sum(left * right for left, right in zip(left_values, right_values))
    left_norm = sqrt(sum(value * value for value in left_values))
    right_norm = sqrt(sum(value * value for value in right_values))
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return max(0.0, min(1.0, dot / (left_norm * right_norm)))


def _confidence(similarity: float, liveness_score: float, spoof_score: float) -> float:
    raw = (similarity * 0.55) + (liveness_score * 0.35) + ((1.0 - spoof_score) * 0.10)
    return round(max(0.0, min(1.0, raw)), 3)


def _normalize(value: str) -> str:
    return " ".join(value.lower().strip().split())


def transaction_binding(
    source_account_id: str,
    beneficiary_id: str,
    amount: str,
    currency: str,
    reference: str,
) -> str:
    try:
        normalized_amount = str(Decimal(amount).quantize(Decimal("0.01")))
    except (InvalidOperation, ValueError) as error:
        raise VoiceServiceError("payment amount is invalid") from error
    canonical = "|".join(
        (
            source_account_id.strip(),
            beneficiary_id.strip(),
            normalized_amount,
            currency.strip().upper(),
            _normalize(reference),
        )
    )
    if not source_account_id.strip() or not beneficiary_id.strip() or not reference.strip():
        raise VoiceServiceError("transaction binding fields are required")
    return sha256(canonical.encode("utf-8")).hexdigest()


def _constant_time_equal(left: str, right: str) -> bool:
    if len(left) != len(right):
        return False
    difference = 0
    for left_char, right_char in zip(left.encode("utf-8"), right.encode("utf-8")):
        difference |= left_char ^ right_char
    return difference == 0


def _validate_verification_request(request: VoiceVerificationRequest) -> None:
    _validate_embedding(request.embedding, "verification embedding")
    _require_unit_interval(request.liveness_score, "liveness_score")
    _require_unit_interval(request.spoof_score, "spoof_score")
    _require_unit_interval(request.voice_threshold, "voice_threshold")
    if request.transaction_amount < 0:
        raise VoiceServiceError("transaction amount cannot be negative")
    if not request.audio_fingerprint_hash.strip():
        raise VoiceServiceError("audio fingerprint hash is required")


def _validate_embedding(embedding: Sequence[float], label: str) -> None:
    if len(embedding) == 0:
        raise VoiceServiceError(f"{label} cannot be empty")


def _require_unit_interval(value: float, label: str) -> None:
    if value < 0.0 or value > 1.0:
        raise VoiceServiceError(f"{label} must be between 0.0 and 1.0")
