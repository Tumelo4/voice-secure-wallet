package com.voicesecure.api;

final class ApiJson {
    private ApiJson() {
    }

    static String stringField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("missing json field: " + field);
        }
        int valueStart = start + marker.length();
        int end = json.indexOf('"', valueStart);
        if (end < 0) {
            throw new IllegalArgumentException("invalid json string field: " + field);
        }
        return json.substring(valueStart, end);
    }

    static long longField(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("missing json field: " + field);
        }
        int valueStart = start + marker.length();
        int end = valueStart;
        while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        if (end == valueStart) {
            throw new IllegalArgumentException("invalid json numeric field: " + field);
        }
        return Long.parseLong(json.substring(valueStart, end));
    }

    static String objectField(String json, String field) {
        String marker = "\"" + field + "\":";
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) throw new IllegalArgumentException("missing json field: " + field);
        int start = markerIndex + marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '{') throw new IllegalArgumentException("invalid json object field: " + field);
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < json.length(); index++) {
            char value = json.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') quoted = true;
            else if (value == '{') depth++;
            else if (value == '}' && --depth == 0) return json.substring(start, index + 1);
        }
        throw new IllegalArgumentException("invalid json object field: " + field);
    }

    static String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }
}
