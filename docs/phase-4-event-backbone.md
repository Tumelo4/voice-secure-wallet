# Phase 4 Event Backbone

This slice captures the shared event language described in the build plan:

- universal event envelopes;
- explicit topic definitions;
- topic-aware outbox relay;
- partition-key metadata for each topic;
- adapter methods on service events so domain records can be published without
  copying payload logic into infrastructure code.

The relay is in-memory for now, but it preserves the append-only semantics and
ordering expectations the Kafka layer will need later.
