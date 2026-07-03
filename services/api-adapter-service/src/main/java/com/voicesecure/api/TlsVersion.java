package com.voicesecure.api;

public enum TlsVersion {
    TLS_1_2,
    TLS_1_3;

    boolean isAtLeast(TlsVersion requiredVersion) {
        return ordinal() >= requiredVersion.ordinal();
    }

    String displayName() {
        return switch (this) {
            case TLS_1_2 -> "TLS 1.2";
            case TLS_1_3 -> "TLS 1.3";
        };
    }
}
