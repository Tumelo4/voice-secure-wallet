# voice-service

Python voice-verification core for VoiceSecure Wallet.

Maturity and remaining external evaluation gaps are tracked in the generated
[`capability status`](../../docs/product/current-capability-status.md).

The bundled inference adapter is an experimental trust-boundary fixture, not a
validated biometric authenticator. `VOICE_AUTH_MODE` defaults to `demo` and may
be `disabled`, `demo`, `shadow`, or `enforced`. Demo and shadow results always
require fallback authentication. Enforced startup additionally requires
`VOICE_AUTH_INDEPENDENTLY_APPROVED=true`; that flag records deployment intent
but does not replace the independent evaluation and named approvals required by
the capability registry. `VOICE_ONLY` is rejected in every mode.

## Problem Statement

The platform needs a high-signal, low-friction way to verify a user without
forcing them through passwords and repeated manual checks. Voice verification
has to be strong enough to be useful and resilient enough to support fallback.

## Impact

- Users get a faster authentication path with less friction.
- Support teams see fewer routine verification calls and password resets.
- The business gains a distinctive, secure control that supports conversion and
  reduces account abuse.

## Scope

This slice implements the server-side trust boundary for enrollment,
challenge issuance, conservative interim liveness/spoof feature extraction,
replay detection, and fallback selection. It is explicitly
`NOT_PRODUCTION_READY` until an independently evaluated speaker and anti-spoof
model replaces the bundled adapter.

## Benchmark

- Enrollment requires exactly three non-empty raw-audio samples; embeddings
  are extracted behind the server-owned `VoiceInferenceAdapter` port.
- Verification should stay under 25 ms locally for in-memory profiles.
- Target false-positive rate for shadow-mode launch evidence is below 0.1%.
- Clients cannot submit embeddings, liveness scores, spoof scores, fingerprints,
  or thresholds. Those values are computed inside the service trust boundary.
- A challenge is single-use, even when the second attempt has a different
  fingerprint.

## How To Use It

The service uses a standard `src/` package layout and supports Python 3.9+.

```python
repository = InMemoryVoiceRepository()
service = VoiceService(repository)
profile = service.enroll(user_id, samples)
challenge = service.issue_challenge(user_id, "open sesame")
result = service.verify(request)
```

`VoiceService` depends on `VoiceRepository` and `VoiceInferenceAdapter` ports,
so durable storage and a validated model/vendor adapter can replace the in-memory
and conservative interim implementations without changing verification policy.

## Local Test Command

```bash
python -m pip install -e ".[test]"
python -m pytest
```

Build the wheel and source distribution through the PEP 517 interface:

```bash
python -m build
```
