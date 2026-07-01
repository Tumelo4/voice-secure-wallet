package com.voicesecure.notifications;

final class NotificationJson {
    private NotificationJson() {
    }

    static String stringField(String json, String field) {
        String value = optionalStringField(json, field, null);
        if (value == null) {
            throw new NotificationException("missing json field: " + field);
        }
        return value;
    }

    static String optionalStringField(String json, String field, String fallback) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + marker.length();
        int end = json.indexOf('"', valueStart);
        if (end < 0) {
            throw new NotificationException("invalid json string field: " + field);
        }
        return json.substring(valueStart, end);
    }

    static String optionalRawField(String json, String field, String fallback) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + marker.length();
        int end = valueStart;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        if (end == valueStart) {
            return fallback;
        }
        return json.substring(valueStart, end).replace("\"", "").trim();
    }
}
