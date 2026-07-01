package com.voicesecure.wallet;

public final class WalletException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WalletException(String message) {
        super(message);
    }
}
