CREATE TABLE notification_deliveries (
  delivery_id UUID PRIMARY KEY,
  source_event_id UUID NOT NULL UNIQUE,
  source_event_type TEXT NOT NULL,
  channel TEXT NOT NULL CHECK (channel IN ('PUSH','SMS','EMAIL','OTP')),
  recipient_ref TEXT NOT NULL,
  trace_id TEXT NOT NULL,
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notification_inbox (
  consumer_name TEXT NOT NULL,
  event_id UUID NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (consumer_name,event_id)
);
