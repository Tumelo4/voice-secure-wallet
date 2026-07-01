package com.voicesecure.wallet;

final class WalletJson {
    private WalletJson() {
    }

    static String stringField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new WalletException("missing json field: " + field);
        }
        int valueStart = start + marker.length();
        int end = json.indexOf('"', valueStart);
        if (end < 0) {
            throw new WalletException("invalid json string field: " + field);
        }
        return json.substring(valueStart, end);
    }

    static long longField(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new WalletException("missing json field: " + field);
        }
        int valueStart = start + marker.length();
        int end = valueStart;
        while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        if (end == valueStart) {
            throw new WalletException("invalid json numeric field: " + field);
        }
        return Long.parseLong(json.substring(valueStart, end));
    }
}
