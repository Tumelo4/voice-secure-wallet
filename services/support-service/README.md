# support-service

Support operations core for VoiceSecure Wallet.

This service keeps a read-only ledger replica for transaction search, tracks
account freeze and dispute cases, and links repair escalations to the ledger
repair flow.

## Current Guarantees

- Search is driven from a projected ledger replica.
- Account freezes and disputes are audit-backed.
- Repair requests remain linked to the signed ledger repair path.

