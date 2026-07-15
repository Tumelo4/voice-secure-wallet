# MSK event-flow evidence

Status: live test not yet run.

Capture correlated, redacted evidence for `payment.initiated`, `payment.authorised`, `ledger.posted`, `payment.completed`, and `notification.requested`. Demonstrate producer delivery, ledger and notification consumption, duplicate event-ID rejection, dead-letter routing, and consumer lag in CloudWatch. Record topic configuration and destroy MSK after capture.
