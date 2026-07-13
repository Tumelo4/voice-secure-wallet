# Secret management

Production secrets live in a managed secret store, are referenced by workload identity, encrypted with managed keys, rotated and never committed or placed in images. CI uses short-lived OIDC credentials. Logs and build output redact tokens. Rotation runbooks cover database, broker, signing and provider credentials; emergency revocation is audited.

