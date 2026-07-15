package com.voicesecure.events;

public final class PermanentEventPublicationException extends EventEnvelopeException {
    public PermanentEventPublicationException(String message) {
        super(message);
    }

    public PermanentEventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
