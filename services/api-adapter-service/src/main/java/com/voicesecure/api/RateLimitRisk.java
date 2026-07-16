package com.voicesecure.api;

public enum RateLimitRisk {
    LOW, MEDIUM, HIGH;

    public static RateLimitRisk forRequest(String method, String path) {
        String normalized = path == null ? "" : path.toLowerCase();
        if (normalized.contains("login") || normalized.contains("voice") ||
                normalized.contains("payment") || normalized.contains("repair")) {
            return HIGH;
        }
        return "GET".equalsIgnoreCase(method) ? LOW : MEDIUM;
    }
}
