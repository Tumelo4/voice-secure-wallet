# Phase 5 Voice Biometrics

This slice adds the voice verification core described in the build plan:

- 3-sample enrollment with averaged embeddings;
- 30-second challenge TTL;
- passive and active liveness scoring;
- transcript and fingerprint checks;
- deterministic fallback selection when voice fails;
- replay detection through audio fingerprint hashes.

The implementation is pure Python for now so the biometric logic can be tested
without wiring FastAPI or external audio libraries into this branch yet.
