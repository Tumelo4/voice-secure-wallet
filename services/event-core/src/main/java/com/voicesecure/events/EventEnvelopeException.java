package com.voicesecure.events;

public class EventEnvelopeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EventEnvelopeException(String message) {
        super(message);
    }
}

