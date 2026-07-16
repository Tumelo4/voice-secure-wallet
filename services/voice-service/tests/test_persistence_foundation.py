from datetime import timedelta
from pathlib import Path
import pytest
from voice_service.persistence import VoicePersistenceConfig

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
