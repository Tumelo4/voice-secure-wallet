package com.voicesecure.identity;

import java.util.LinkedHashMap;
import java.util.Map;

final class JwtJson {
    private JwtJson() {
    }

    static Map<String, String> read(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IdentityException("invalid token json");
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return values;
        }
        for (String part : body.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                throw new IdentityException("invalid token json field");
            }
            values.put(unquote(kv[0]), unquote(kv[1]));
        }
        return values;
    }

    static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    private static String unquote(String value) {
        if (value == null) {
            throw new IdentityException("missing claim");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return trimmed;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
