from __future__ import annotations
from contextlib import contextmanager
from datetime import timedelta
from typing import Any, Iterator
from uuid import UUID
from psycopg2.extras import RealDictCursor, register_uuid
from psycopg2.pool import ThreadedConnectionPool
from . import (FallbackMethod, VoiceChallenge, VoiceProfile, VoiceRepository,
               VoiceStatus, VoiceVerificationResult)
from .persistence import VoiceTemplateCipher

class PostgresVoiceRepository(VoiceRepository):
    def __init__(self, dsn: str, cipher: VoiceTemplateCipher, model_version: str,
                 retention: timedelta = timedelta(days=365)) -> None:
        register_uuid()
        self._pool = ThreadedConnectionPool(1, 10, dsn)
        self._cipher = cipher
        self._model_version = model_version
        self._retention = retention

    def close(self) -> None: self._pool.closeall()

    @contextmanager
    def _connection(self) -> Iterator["_Session"]:
        raw = self._pool.getconn()
        try:
            yield _Session(raw)
            raw.commit()
        except Exception:
            raw.rollback()
            raise
        finally:
            self._pool.putconn(raw)

    def save_profile(self, profile: VoiceProfile) -> None:
        encrypted = self._cipher.encrypt(profile.user_id, profile.embedding, self._model_version)
        with self._connection() as connection:
            connection.execute("""INSERT INTO voice_profiles
              (user_id,template_ciphertext,encrypted_data_key,nonce,key_reference,algorithm,model_version,
               consented_at,enrolled_at,retain_until) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
              ON CONFLICT(user_id) DO UPDATE SET template_ciphertext=EXCLUDED.template_ciphertext,
               encrypted_data_key=EXCLUDED.encrypted_data_key,nonce=EXCLUDED.nonce,key_reference=EXCLUDED.key_reference,
               algorithm=EXCLUDED.algorithm,model_version=EXCLUDED.model_version,enrolled_at=EXCLUDED.enrolled_at,
               revoked_at=NULL,deleted_at=NULL""", (profile.user_id, encrypted.ciphertext, encrypted.encrypted_data_key,
               encrypted.nonce, encrypted.key_reference, encrypted.algorithm, encrypted.model_version,
               profile.enrolled_at, profile.enrolled_at, profile.enrolled_at + self._retention))
            self._audit(connection, profile.user_id, "VOICE_ENROLLED")

    def get_profile(self, user_id: UUID) -> VoiceProfile | None:
        with self._connection() as connection:
            row = connection.execute("SELECT * FROM voice_profiles WHERE user_id=%s AND revoked_at IS NULL AND deleted_at IS NULL",(user_id,)).fetchone()
        if not row: return None
        from .persistence import EncryptedVoiceTemplate
        encrypted = EncryptedVoiceTemplate(user_id,bytes(row["template_ciphertext"]),bytes(row["encrypted_data_key"]),
            bytes(row["nonce"]),row["key_reference"],row["algorithm"],row["model_version"])
        return VoiceProfile(user_id,self._cipher.decrypt(encrypted),row["enrolled_at"],3)

    def revoke_profile(self, user_id: UUID) -> bool:
        with self._connection() as connection:
            updated = connection.execute(
                "UPDATE voice_profiles SET revoked_at=now() "
                "WHERE user_id=%s AND revoked_at IS NULL AND deleted_at IS NULL",
                (user_id,),
            ).rowcount
            if updated:
                self._audit(connection, user_id, "VOICE_ENROLLMENT_REVOKED")
        return updated == 1

    def delete_profile(self, user_id: UUID) -> bool:
        with self._connection() as connection:
            connection.execute("DELETE FROM voice_replay_fingerprints WHERE user_id=%s", (user_id,))
            deleted = connection.execute("DELETE FROM voice_profiles WHERE user_id=%s", (user_id,)).rowcount
            if deleted:
                self._audit(connection, user_id, "VOICE_ENROLLMENT_DELETED")
        return deleted == 1

    def save_challenge(self, value: VoiceChallenge) -> None:
        with self._connection() as c: c.execute("INSERT INTO voice_challenges VALUES (%s,%s,%s,%s,%s,%s,NULL)",
            (value.challenge_id,value.user_id,value.phrase,value.transaction_binding_hash,value.issued_at,value.expires_at))
    def get_challenge(self, challenge_id: UUID) -> VoiceChallenge | None:
        with self._connection() as c: row=c.execute("SELECT * FROM voice_challenges WHERE challenge_id=%s",(challenge_id,)).fetchone()
        return None if not row else VoiceChallenge(row["challenge_id"],row["user_id"],row["phrase"],row["issued_at"],row["expires_at"],row["transaction_binding_hash"])
    def mark_challenge_attempted(self, challenge_id: UUID) -> None:
        with self._connection() as c:
            updated=c.execute("UPDATE voice_challenges SET attempted_at=now() WHERE challenge_id=%s AND attempted_at IS NULL",(challenge_id,)).rowcount
            if updated != 1: raise ValueError("challenge already attempted or missing")
    def challenge_attempted(self, challenge_id: UUID) -> bool:
        with self._connection() as c: row=c.execute("SELECT attempted_at IS NOT NULL AS attempted FROM voice_challenges WHERE challenge_id=%s",(challenge_id,)).fetchone()
        return bool(row and row["attempted"])
    def remember_fingerprint(self,user_id:UUID,audio_fingerprint_hash:str)->None:
        with self._connection() as c: c.execute("INSERT INTO voice_replay_fingerprints VALUES (%s,%s,now()) ON CONFLICT DO NOTHING",(user_id,audio_fingerprint_hash))
    def fingerprint_seen(self,user_id:UUID,audio_fingerprint_hash:str)->bool:
        with self._connection() as c: return c.execute("SELECT 1 FROM voice_replay_fingerprints WHERE user_id=%s AND fingerprint_hash=%s",(user_id,audio_fingerprint_hash)).fetchone() is not None
    def save_session(self,v:VoiceVerificationResult)->None:
        with self._connection() as c:
            c.execute("INSERT INTO voice_verification_decisions VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",(v.verification_id,v.user_id,v.challenge_id,v.status.value,v.confidence,v.fallback_requested,v.fallback_method.value if v.fallback_method else None,v.reason,self._model_version,v.verified_at));self._audit(c,v.user_id,"VOICE_VERIFIED")
    def sessions(self)->list[VoiceVerificationResult]:
        with self._connection() as c: rows=c.execute("SELECT * FROM voice_verification_decisions ORDER BY verified_at,verification_id").fetchall()
        return [VoiceVerificationResult(r["verification_id"],r["user_id"],r["challenge_id"],VoiceStatus(r["status"]),r["confidence"],r["fallback_requested"],FallbackMethod(r["fallback_method"]) if r["fallback_method"] else None,r["reason"],r["verified_at"]) for r in rows]
    @staticmethod
    def _audit(connection:Any,user_id:UUID,action:str)->None:
        from uuid import uuid4
        connection.execute("INSERT INTO voice_audit_events VALUES (%s,%s,%s,%s,%s,now())",(uuid4(),user_id,action,"voice-service","{}"))


class _Session:
    def __init__(self, connection: Any) -> None:
        self._connection = connection

    def execute(self, statement: str, parameters: Any = None) -> Any:
        cursor = self._connection.cursor(cursor_factory=RealDictCursor)
        cursor.execute(statement, parameters)
        return cursor
