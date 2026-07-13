# Data classification

| Class | Examples | Handling |
|---|---|---|
| Restricted | Tokens, biometric templates, raw voice, secrets | Encrypt, least privilege, never log, shortest retention |
| Confidential | Identity, account numbers, payment and compliance records | Encrypt, mask, audit access, statutory retention |
| Internal | Saga/event identifiers, risk signals, traces | Staff/system only; redact from customer APIs |
| Public | Product copy and public API descriptions | Integrity controlled |

Raw voice is not retained unless separately justified and consented. Authorisation decisions retain challenge/payment bindings and evidence references.

