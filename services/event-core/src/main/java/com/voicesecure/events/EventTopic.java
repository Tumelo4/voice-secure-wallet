package com.voicesecure.events;

public enum EventTopic {
    PAYMENTS("payments", "payment_id"),
    LEDGER("ledger", "account_id"),
    FRAUD("fraud", "user_id"),
    VOICE("voice", "user_id"),
    COMPLIANCE("compliance", "user_id"),
    IDENTITY("identity", "user_id");

    private final String topicName;
    private final String partitionKeyField;

    EventTopic(String topicName, String partitionKeyField) {
        this.topicName = topicName;
        this.partitionKeyField = partitionKeyField;
    }

    public String topicName() {
        return topicName;
    }

    public String partitionKeyField() {
        return partitionKeyField;
    }
}

