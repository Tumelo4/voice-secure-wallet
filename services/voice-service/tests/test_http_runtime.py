import json
from datetime import datetime, timezone

import pytest

from voice_service import InMemoryVoiceRepository, VoiceService, VoiceServiceError
from voice_service.http_runtime import HttpPaymentOutcomePublisher, PaymentOutcomePublisher, VoiceHttpApplication


class RecordingOutcomePublisher(PaymentOutcomePublisher):
    def __init__(self) -> None:
        self.outcomes = []

    def publish(self, payment_reference, result) -> None:
        self.outcomes.append((payment_reference, result.status.value, result.verification_id))


def test_health_is_public_and_service_routes_require_authentication() -> None:
    app = VoiceHttpApplication(VoiceService(InMemoryVoiceRepository()), "test-token")
    assert app.handle("GET", "/health/ready", {}, b"")[0] == 200
    status, body = app.handle("POST", "/v1/voice/challenges", {}, b"{}")
    assert status == 401
    assert body["code"] == "SERVICE_AUTHENTICATION_REQUIRED"


def test_enrollment_contract_does_not_echo_biometric_embedding() -> None:
    app = VoiceHttpApplication(VoiceService(InMemoryVoiceRepository()), "test-token")
    payload = b'{"userId":"11111111-1111-4111-8111-111111111111","samples":[[1,0],[1,0],[1,0]]}'
    status, body = app.handle("POST", "/v1/voice/enrollments", {"x-service-token": "test-token"}, payload)
    assert status == 201
    assert body["sampleCount"] == 3
    assert "embedding" not in body


def test_challenge_and_verification_contract_complete_a_bound_transaction() -> None:
    publisher = RecordingOutcomePublisher()
    app = VoiceHttpApplication(VoiceService(InMemoryVoiceRepository()), "test-token", publisher)
    headers = {"x-service-token": "test-token"}
    user_id = "11111111-1111-4111-8111-111111111111"
    enrollment = {"userId": user_id, "samples": [[1, 0], [1, 0], [1, 0]]}
    assert app.handle("POST", "/v1/voice/enrollments", headers, json.dumps(enrollment).encode())[0] == 201

    binding = "transaction-binding-sha256"
    challenge_status, challenge = app.handle(
        "POST", "/v1/voice/challenges", headers,
        json.dumps({"userId": user_id, "phrase": "Confirm payment", "ttlSeconds": 30,
                    "transactionBindingHash": binding}).encode(),
    )
    assert challenge_status == 201
    assert challenge["phrase"] == "Confirm payment"

    verification = {
        "userId": user_id,
        "challengeId": challenge["challengeId"],
        "transcript": "Confirm payment",
        "embedding": [1, 0],
        "livenessScore": 0.95,
        "spoofScore": 0.01,
        "audioFingerprintHash": "unique-audio-hash",
        "authPolicy": "VOICE_OTP",
        "transactionAmountMinor": 75000,
        "voiceThreshold": 0.8,
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "transactionBindingHash": binding,
        "paymentReference": "VSW-safe_reference",
    }
    status, body = app.handle(
        "POST", "/v1/voice/verifications", headers, json.dumps(verification).encode()
    )
    assert status == 200
    assert body["status"] == "VERIFIED"
    assert body["fallback_method"] is None
    assert publisher.outcomes[0][0:2] == ("VSW-safe_reference", "VERIFIED")


def test_invalid_payload_and_unknown_route_return_safe_errors() -> None:
    app = VoiceHttpApplication(VoiceService(InMemoryVoiceRepository()), "test-token")
    headers = {"x-service-token": "test-token"}
    status, body = app.handle("POST", "/v1/voice/challenges", headers, b"not-json")
    assert status == 400
    assert body == {"code": "VOICE_REQUEST_INVALID", "message": "Review the voice request and try again."}
    status, body = app.handle("POST", "/v1/voice/unknown", headers, b"{}")
    assert status == 404
    assert body["code"] == "ROUTE_NOT_FOUND"


def test_blank_service_token_is_rejected() -> None:
    with pytest.raises(VoiceServiceError, match="VOICE_SERVICE_TOKEN"):
        VoiceHttpApplication(VoiceService(InMemoryVoiceRepository()), " ")


def test_http_outcome_publisher_uses_service_bearer_token(monkeypatch) -> None:
    captured = {}

    class Response:
        status = 202

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

    def fake_urlopen(request, timeout):
        captured["request"] = request
        captured["timeout"] = timeout
        return Response()

    monkeypatch.setattr("voice_service.http_runtime.urllib.request.urlopen", fake_urlopen)
    result = type("Result", (), {
        "status": type("Status", (), {"value": "VERIFIED"})(),
        "confidence": 0.94,
        "verification_id": "verification-1",
    })()
    HttpPaymentOutcomePublisher("https://payments.internal/", "service-token").publish("VSW-reference", result)

    request = captured["request"]
    assert request.full_url.endswith("/internal/payments/VSW-reference/voice-outcomes")
    assert request.headers["Authorization"] == "Bearer service-token"
    assert json.loads(request.data)["status"] == "APPROVED"
    assert captured["timeout"] == 5
