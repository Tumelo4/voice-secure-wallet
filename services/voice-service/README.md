# voice-service

Python voice-verification core for VoiceSecure Wallet.

This slice implements enrollment, challenge issuance, liveness scoring,
replay detection, and fallback selection.

## Benchmark

- Enrollment requires exactly three non-empty voice embeddings.
- Verification should stay under 25 ms locally for in-memory profiles.
- Target false-positive rate for shadow-mode launch evidence is below 0.1%.
- Liveness and spoof scores must stay in the `0.0..1.0` range.
- A challenge is single-use, even when the second attempt has a different
  fingerprint.

## How To Use It

Use Python 3.10+ because the service uses `dataclass(slots=True)`.

```python
repository = InMemoryVoiceRepository()
service = VoiceService(repository)
profile = service.enroll(user_id, samples)
challenge = service.issue_challenge(user_id, "open sesame")
result = service.verify(request)
```

`VoiceService` depends on the `VoiceRepository` protocol, so durable storage can
replace `InMemoryVoiceRepository` without changing verification policy.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

