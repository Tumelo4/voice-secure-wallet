package com.voicesecure.fraud;

public class FraudException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FraudException(String message) {
        super(message);
    }
}
