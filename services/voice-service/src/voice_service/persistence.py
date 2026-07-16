from __future__ import annotations
from dataclasses import dataclass
from datetime import timedelta
from typing import Protocol
from uuid import UUID
from urllib.parse import urlparse

@dataclass(frozen=True)
class EncryptedVoiceTemplate:
    user_id: UUID
    ciphertext: bytes
    encrypted_data_key: bytes
    nonce: bytes
    key_reference: str
    algorithm: str
    model_version: str

    def __post_init__(self) -> None:
        if not self.ciphertext or not self.encrypted_data_key or not self.nonce:
            raise ValueError("encrypted template fields are required")
        if self.algorithm != "AES-256-GCM":
            raise ValueError("templates require AES-256-GCM envelope encryption")
        if not self.key_reference.strip() or not self.model_version.strip():
            raise ValueError("key reference and model version are required")

class VoiceTemplateCipher(Protocol):
    def encrypt(self, user_id: UUID, embedding: tuple[float, ...], model_version: str) -> EncryptedVoiceTemplate: ...
    def decrypt(self, template: EncryptedVoiceTemplate) -> tuple[float, ...]: ...

@dataclass(frozen=True)
class VoicePersistenceConfig:
    postgres_dsn: str
    kms_key_reference: str
    retention: timedelta
    model_version: str

    def __post_init__(self) -> None:
        parsed = urlparse(self.postgres_dsn)
        if parsed.scheme not in {"postgresql", "postgres"} or not parsed.hostname:
            raise ValueError("VOICE_DATABASE_URL must be a PostgreSQL DSN")
        if "sslmode=require" not in parsed.query and "sslmode=verify-full" not in parsed.query:
            raise ValueError("voice PostgreSQL requires TLS")
        if not self.kms_key_reference.strip() or not self.model_version.strip():
            raise ValueError("KMS key reference and model version are required")
        if self.retention <= timedelta(0) or self.retention > timedelta(days=365):
            raise ValueError("voice retention must be between 1 second and 365 days")
