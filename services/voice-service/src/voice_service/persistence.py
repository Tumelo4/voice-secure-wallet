from __future__ import annotations
from dataclasses import dataclass
from datetime import timedelta
from typing import Any, Protocol
from uuid import UUID
from urllib.parse import urlparse
import json, os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

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

class DataKeyProvider(Protocol):
    def generate_data_key(self, key_reference: str) -> tuple[bytes, bytes]: ...
    def decrypt_data_key(self, key_reference: str, encrypted_data_key: bytes) -> bytes: ...

class AwsKmsDataKeyProvider:
    """AWS KMS envelope-key adapter; plaintext data keys never leave this process."""

    _CONTEXT = {"service": "voice-service", "purpose": "voice-template"}

    def __init__(self, client: Any) -> None:
        self._client = client

    def generate_data_key(self, key_reference: str) -> tuple[bytes, bytes]:
        response = self._client.generate_data_key(
            KeyId=key_reference,
            KeySpec="AES_256",
            EncryptionContext=self._CONTEXT,
        )
        plaintext = bytes(response["Plaintext"])
        encrypted = bytes(response["CiphertextBlob"])
        self._validate_key(plaintext)
        if not encrypted:
            raise ValueError("AWS KMS returned an empty encrypted data key")
        return plaintext, encrypted

    def decrypt_data_key(self, key_reference: str, encrypted_data_key: bytes) -> bytes:
        if not encrypted_data_key:
            raise ValueError("encrypted data key is required")
        response = self._client.decrypt(
            KeyId=key_reference,
            CiphertextBlob=encrypted_data_key,
            EncryptionContext=self._CONTEXT,
        )
        plaintext = bytes(response["Plaintext"])
        self._validate_key(plaintext)
        return plaintext

    @staticmethod
    def _validate_key(key: bytes) -> None:
        if len(key) != 32:
            raise ValueError("AWS KMS must return a 256-bit data key")

class EnvelopeVoiceTemplateCipher:
    def __init__(self, keys: DataKeyProvider, key_reference: str) -> None:
        self._keys, self._key_reference = keys, key_reference
    def encrypt(self, user_id: UUID, embedding: tuple[float, ...], model_version: str) -> EncryptedVoiceTemplate:
        key, encrypted_key = self._keys.generate_data_key(self._key_reference); nonce = os.urandom(12)
        ciphertext = AESGCM(key).encrypt(nonce, json.dumps(embedding).encode(), user_id.bytes)
        return EncryptedVoiceTemplate(user_id,ciphertext,encrypted_key,nonce,self._key_reference,"AES-256-GCM",model_version)
    def decrypt(self, template: EncryptedVoiceTemplate) -> tuple[float, ...]:
        key = self._keys.decrypt_data_key(template.key_reference, template.encrypted_data_key)
        return tuple(json.loads(AESGCM(key).decrypt(template.nonce,template.ciphertext,template.user_id.bytes)))

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
