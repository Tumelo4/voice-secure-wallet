package com.voicesecure.ops;

public final class OpsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OpsException(String message) {
        super(message);
    }
}
