from datetime import timedelta
from pathlib import Path
import pytest
from voice_service.persistence import AwsKmsDataKeyProvider, VoicePersistenceConfig

def test_schema_never_declares_plain_embedding_or_raw_audio() -> None:
    sql = Path("migrations/V001__voice_persistence.sql").read_text()
    for table in ("voice_profiles", "voice_challenges", "voice_replay_fingerprints",
                  "voice_verification_decisions", "voice_audit_events"):
        assert f"CREATE TABLE {table}" in sql
    assert "template_ciphertext BYTEA" in sql
    assert "raw_audio" not in sql and " embedding " not in sql

def test_production_config_requires_tls_and_bounded_retention() -> None:
    VoicePersistenceConfig("postgresql://db/voice?sslmode=verify-full", "kms-key", timedelta(days=30), "model-1")
    with pytest.raises(ValueError, match="TLS"):
        VoicePersistenceConfig("postgresql://db/voice", "kms-key", timedelta(days=30), "model-1")
    with pytest.raises(ValueError, match="365"):
        VoicePersistenceConfig("postgresql://db/voice?sslmode=require", "kms-key", timedelta(days=366), "model-1")

def test_aws_kms_data_keys_are_bound_to_voice_template_context() -> None:
    class KmsClient:
        def __init__(self) -> None:
            self.calls = []
        def generate_data_key(self, **request):
            self.calls.append(("generate", request))
            return {"Plaintext": b"p" * 32, "CiphertextBlob": b"encrypted-key"}
        def decrypt(self, **request):
            self.calls.append(("decrypt", request))
            return {"Plaintext": b"p" * 32}

    client = KmsClient()
    provider = AwsKmsDataKeyProvider(client)
    assert provider.generate_data_key("arn:aws:kms:af-south-1:123:key/voice") == (b"p" * 32, b"encrypted-key")
    assert provider.decrypt_data_key("arn:aws:kms:af-south-1:123:key/voice", b"encrypted-key") == b"p" * 32
    assert client.calls[0][1]["KeySpec"] == "AES_256"
    assert client.calls[0][1]["EncryptionContext"] == client.calls[1][1]["EncryptionContext"]
    assert client.calls[1][1]["KeyId"].endswith("key/voice")

def test_aws_kms_rejects_malformed_plaintext_keys() -> None:
    client = type("KmsClient", (), {"generate_data_key": lambda self, **_: {
        "Plaintext": b"short", "CiphertextBlob": b"encrypted"
    }})()
    with pytest.raises(ValueError, match="256-bit"):
        AwsKmsDataKeyProvider(client).generate_data_key("kms-key")
