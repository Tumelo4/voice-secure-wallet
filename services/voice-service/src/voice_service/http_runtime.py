from __future__ import annotations

import json
import os
import urllib.request
from base64 import b64decode
from dataclasses import asdict
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Callable, Mapping, Optional
from uuid import UUID

from . import (
    AuthPolicy,
    RawAudioSample,
    VoiceService,
    VoiceServiceError,
    VoiceAuthMode,
    VoiceVerificationRequest,
)
from .persistence import AwsKmsDataKeyProvider, EnvelopeVoiceTemplateCipher, VoicePersistenceConfig
from .postgres_repository import PostgresVoiceRepository


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


def build_production_application(
    environment: Mapping[str, str],
    kms_client_factory: Optional[Callable[[], Any]] = None,
    repository_factory: Callable[..., Any] = PostgresVoiceRepository,
) -> tuple[VoiceHttpApplication, Any]:
    """Compose production adapters from validated deployment configuration."""
    try:
        retention_days = int(environment.get("VOICE_RETENTION_DAYS", "365"))
        config = VoicePersistenceConfig(
            environment["VOICE_DATABASE_URL"],
            environment["VOICE_KMS_KEY_ARN"],
            timedelta(days=retention_days),
            environment["VOICE_MODEL_VERSION"],
        )
        auth_mode = VoiceAuthMode(environment.get("VOICE_AUTH_MODE", VoiceAuthMode.DEMO.value).lower())
        service_token = environment["VOICE_SERVICE_TOKEN"]
    except KeyError as error:
        raise VoiceServiceError(f"{error.args[0]} is required") from error
    except ValueError as error:
        raise VoiceServiceError(f"invalid voice production configuration: {error}") from error

    if kms_client_factory is None:
        import boto3
        kms_client = boto3.client("kms")
    else:
        kms_client = kms_client_factory()
    cipher = EnvelopeVoiceTemplateCipher(AwsKmsDataKeyProvider(kms_client), config.kms_key_reference)
    repository = repository_factory(config.postgres_dsn, cipher, config.model_version, config.retention)
    publisher = None
    if environment.get("PAYMENT_API_URL"):
        try:
            publisher = HttpPaymentOutcomePublisher(
                environment["PAYMENT_API_URL"], environment["PAYMENT_API_SERVICE_TOKEN"])
        except KeyError as error:
            repository.close()
            raise VoiceServiceError(f"{error.args[0]} is required when PAYMENT_API_URL is set") from error
    independently_approved = environment.get("VOICE_AUTH_INDEPENDENTLY_APPROVED", "false").lower() == "true"
    try:
        app = VoiceHttpApplication(
            VoiceService(repository, auth_mode=auth_mode, enforced_mode_approved=independently_approved),
            service_token,
            publisher,
        )
    except Exception:
        repository.close()
        raise
    return app, repository


def run() -> None:  # pragma: no cover - exercised by the container health smoke test
    # Container ingress requires binding all interfaces; network exposure is
    # restricted by the platform security group and service authentication.
    host = os.getenv("VOICE_HOST", "0.0.0.0")  # nosec B104
    port = int(os.getenv("VOICE_PORT", "8090"))
    app, repository = build_production_application(os.environ)

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

    try:
        ThreadingHTTPServer((host, port), Handler).serve_forever()
    finally:
        repository.close()
