CREATE TABLE voice_profiles (user_id UUID PRIMARY KEY, template_ciphertext BYTEA NOT NULL,
 encrypted_data_key BYTEA NOT NULL, nonce BYTEA NOT NULL, key_reference TEXT NOT NULL,
 algorithm TEXT NOT NULL CHECK (algorithm = 'AES-256-GCM'), model_version TEXT NOT NULL,
 consented_at TIMESTAMPTZ NOT NULL, enrolled_at TIMESTAMPTZ NOT NULL, retain_until TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ);
CREATE TABLE voice_challenges (challenge_id UUID PRIMARY KEY, user_id UUID NOT NULL, phrase TEXT NOT NULL,
 transaction_binding_hash TEXT NOT NULL, issued_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 attempted_at TIMESTAMPTZ);
CREATE TABLE voice_replay_fingerprints (user_id UUID NOT NULL, fingerprint_hash TEXT NOT NULL,
 first_seen_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(user_id, fingerprint_hash));
CREATE TABLE voice_verification_decisions (verification_id UUID PRIMARY KEY, user_id UUID NOT NULL,
 challenge_id UUID NOT NULL, status TEXT NOT NULL, confidence DOUBLE PRECISION NOT NULL,
 fallback_requested BOOLEAN NOT NULL, fallback_method TEXT, reason TEXT NOT NULL,
 model_version TEXT NOT NULL, verified_at TIMESTAMPTZ NOT NULL);
CREATE TABLE voice_audit_events (event_id UUID PRIMARY KEY, user_id UUID, action TEXT NOT NULL,
 actor TEXT NOT NULL, details JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL);
