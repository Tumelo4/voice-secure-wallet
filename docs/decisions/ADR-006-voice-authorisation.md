# ADR-006: Voice authorisation

Status: Accepted

Voice is transaction authorisation, not login identity or financial truth. A challenge is single-use, short-lived and bound to customer, registered device, payment, amount and currency. Approval requires liveness/replay controls and policy confidence. Risk/value limits can require stronger factors, and service failure follows an explicit fallback-MFA policy. Store the decision and evidence references, not unnecessary raw audio.
