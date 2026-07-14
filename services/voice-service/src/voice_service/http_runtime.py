from __future__ import annotations

import json
import os
import urllib.request
from base64 import b64decode
from dataclasses import asdict
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Mapping, Optional
from uuid import UUID

from . import (
    AuthPolicy,
    InMemoryVoiceRepository,
    RawAudioSample,
    VoiceService,
    VoiceServiceError,
    VoiceVerificationRequest,
)


class PaymentOutcomePublisher:
    def publish(self, payment_reference: str, result: Any) -> None:  # pragma: no cover - interface
        raise NotImplementedError


class HttpPaymentOutcomePublisher(PaymentOutcomePublisher):
    def __init__(self, api_base_url: str, bearer_token: str) -> None:
        self._api_base_url = api_base_url.rstrip("/")
        self._bearer_token = bearer_token

    def publish(self, payment_reference: str, result: Any) -> None:
        status = "APPROVED" if result.status.value == "VERIFIED" else result.status.value
        payload = json.dumps({"status": status, "confidence": str(result.confidence),
                              "verificationId": str(result.verification_id)}).encode("utf-8")
        request = urllib.request.Request(
            f"{self._api_base_url}/internal/payments/{payment_reference}/voice-outcomes",
            data=payload, method="POST",
            headers={"Authorization": f"Bearer {self._bearer_token}", "Content-Type": "application/json",
                     "X-Trace-Id": f"voice-{result.verification_id}"},
        )
        # The URL is assembled from the deployment-controlled PAYMENT_API_URL,
        # never from request data; non-HTTP schemes cannot enter this path.
        with urllib.request.urlopen(request, timeout=5) as response:  # nosec B310
            if response.status < 200 or response.status >= 300:
                raise VoiceServiceError("payment outcome callback failed")


class VoiceHttpApplication:
    def __init__(self, service: VoiceService, service_token: str,
                 outcome_publisher: Optional[PaymentOutcomePublisher] = None) -> None:
        if not service_token.strip():
            raise VoiceServiceError("VOICE_SERVICE_TOKEN is required")
        self._service = service
        self._service_token = service_token
        self._outcome_publisher = outcome_publisher

    def handle(
        self, method: str, path: str, headers: Mapping[str, str], body: bytes
    ) -> tuple[int, dict[str, Any]]:
        if method == "GET" and path in {"/health/live", "/health/ready"}:
            return 200, {"status": "LIVE" if path.endswith("live") else "READY"}
        if headers.get("x-service-token", "") != self._service_token:
            return 401, {"code": "SERVICE_AUTHENTICATION_REQUIRED", "message": "Service authentication is required."}
        try:
            payload = json.loads(body.decode("utf-8"))
            if method == "POST" and path == "/v1/voice/enrollments":
                profile = self._service.enroll(
                    UUID(payload["userId"]),
                    tuple(_raw_audio(sample) for sample in payload["audioSamples"]),
                )
                return 201, {"userId": str(profile.user_id), "sampleCount": profile.sample_count}
            if method == "POST" and path == "/v1/voice/challenges":
                challenge = self._service.issue_challenge(
                    UUID(payload["userId"]), payload["phrase"], int(payload.get("ttlSeconds", 30)),
                    payload["transactionBindingHash"],
                )
                return 201, {
                    "challengeId": str(challenge.challenge_id), "expiresAt": challenge.expires_at.isoformat(),
                    "phrase": challenge.phrase,
                }
            if method == "POST" and path == "/v1/voice/verifications":
                forbidden = {"embedding", "livenessScore", "spoofScore", "audioFingerprintHash", "voiceThreshold"}
                if forbidden.intersection(payload):
                    raise VoiceServiceError("client-supplied inference fields are forbidden")
                request = VoiceVerificationRequest(
                    user_id=UUID(payload["userId"]), challenge_id=UUID(payload["challengeId"]),
                    audio=_raw_audio(payload["audio"]),
                    auth_policy=AuthPolicy(payload["authPolicy"]), transaction_amount=int(payload["transactionAmountMinor"]),
                    captured_at=datetime.fromisoformat(payload["capturedAt"].replace("Z", "+00:00")).astimezone(timezone.utc),
                    transaction_binding_hash=payload["transactionBindingHash"],
                )
                result = self._service.verify(request)
                if self._outcome_publisher is not None:
                    payment_reference = payload["paymentReference"].strip()
                    if not payment_reference:
                        raise VoiceServiceError("payment reference is required")
                    self._outcome_publisher.publish(payment_reference, result)
                response = asdict(result)
                response.update({"verification_id": str(result.verification_id), "user_id": str(result.user_id),
                                 "challenge_id": str(result.challenge_id), "status": result.status.value,
                                 "verified_at": result.verified_at.isoformat(),
                                 "fallback_method": result.fallback_method.value if result.fallback_method else None})
                return 200, response
            return 404, {"code": "ROUTE_NOT_FOUND", "message": "Route not found."}
        except (KeyError, TypeError, ValueError, VoiceServiceError, json.JSONDecodeError):
            return 400, {"code": "VOICE_REQUEST_INVALID", "message": "Review the voice request and try again."}


def _raw_audio(payload: Mapping[str, Any]) -> RawAudioSample:
    return RawAudioSample(
        content=b64decode(payload["contentBase64"], validate=True),
        codec=str(payload["codec"]),
        sample_rate_hz=int(payload["sampleRateHz"]),
    )


def run() -> None:  # pragma: no cover - exercised by the container health smoke test
    # Container ingress requires binding all interfaces; network exposure is
    # restricted by the platform security group and service authentication.
    host = os.getenv("VOICE_HOST", "0.0.0.0")  # nosec B104
    port = int(os.getenv("VOICE_PORT", "8090"))
    publisher = None
    if os.getenv("PAYMENT_API_URL"):
        publisher = HttpPaymentOutcomePublisher(
            os.environ["PAYMENT_API_URL"], os.environ["PAYMENT_API_SERVICE_TOKEN"])
    app = VoiceHttpApplication(
        VoiceService(InMemoryVoiceRepository()), os.environ["VOICE_SERVICE_TOKEN"], publisher)

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            self._dispatch()

        def do_POST(self) -> None:  # noqa: N802
            self._dispatch()

        def _dispatch(self) -> None:
            length = int(self.headers.get("Content-Length", "0"))
            status, response = app.handle(
                self.command, self.path, {key.lower(): value for key, value in self.headers.items()},
                self.rfile.read(length),
            )
            encoded = json.dumps(response, separators=(",", ":"), default=str).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def log_message(self, format: str, *args: object) -> None:
            return

    ThreadingHTTPServer((host, port), Handler).serve_forever()
