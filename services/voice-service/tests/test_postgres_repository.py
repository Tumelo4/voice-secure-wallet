from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4
import os
import pytest
from testcontainers.postgres import PostgresContainer
from voice_service import VoiceChallenge, VoiceProfile
from voice_service.persistence import EnvelopeVoiceTemplateCipher
from voice_service.postgres_repository import PostgresVoiceRepository

class LocalKeys:
    def __init__(self): self.keys = {}
    def generate_data_key(self, reference):
        key=os.urandom(32); encrypted=os.urandom(32); self.keys[encrypted]=key; return key,encrypted
    def decrypt_data_key(self, reference, encrypted): return self.keys[encrypted]

def test_envelope_cipher_round_trip_and_authenticated_user_binding():
    cipher=EnvelopeVoiceTemplateCipher(LocalKeys(),"kms-test"); user=uuid4()
    encrypted=cipher.encrypt(user,(0.1,0.2),"model-1")
    assert encrypted.ciphertext != b"[0.1, 0.2]"
    assert cipher.decrypt(encrypted)==pytest.approx((0.1,0.2))

def test_postgres_repository_survives_new_instance_and_enforces_single_attempt():
    with PostgresContainer("postgres:16") as postgres:
        import psycopg2
        dsn=postgres.get_connection_url().replace("postgresql+psycopg2","postgresql")
        with psycopg2.connect(dsn) as connection:
            with connection.cursor() as cursor:
                cursor.execute(Path("migrations/V001__voice_persistence.sql").read_text())
        keys=LocalKeys(); cipher=EnvelopeVoiceTemplateCipher(keys,"kms-test"); now=datetime.now(timezone.utc)
        first=PostgresVoiceRepository(dsn,cipher,"model-1"); user=uuid4()
        first.save_profile(VoiceProfile(user,(0.1,0.2),now,3))
        challenge=VoiceChallenge(uuid4(),user,"confirm",now,now+timedelta(seconds=30),"binding")
        first.save_challenge(challenge); first.mark_challenge_attempted(challenge.challenge_id)
        first.remember_fingerprint(user,"fingerprint"); first.close()
        second=PostgresVoiceRepository(dsn,cipher,"model-1")
        assert second.get_profile(user).embedding==pytest.approx((0.1,0.2))
        assert second.challenge_attempted(challenge.challenge_id)
        assert second.fingerprint_seen(user,"fingerprint")
        with pytest.raises(ValueError,match="already attempted"): second.mark_challenge_attempted(challenge.challenge_id)
        assert second.revoke_profile(user)
        assert second.get_profile(user) is None
        assert not second.revoke_profile(user)
        assert second.delete_profile(user)
        assert second.get_profile(user) is None
        assert not second.fingerprint_seen(user,"fingerprint")
        assert not second.delete_profile(user)
        with psycopg2.connect(dsn) as connection:
            with connection.cursor() as cursor:
                cursor.execute("SELECT action FROM voice_audit_events WHERE user_id=%s ORDER BY occurred_at", (user,))
                actions = [row[0] for row in cursor.fetchall()]
        assert "VOICE_ENROLLMENT_REVOKED" in actions
        assert "VOICE_ENROLLMENT_DELETED" in actions
        second.close()
