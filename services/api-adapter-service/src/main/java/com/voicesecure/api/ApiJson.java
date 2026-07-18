package com.voicesecure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ApiJson {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ApiJson() {
    }

    static String stringField(String json, String field) {
        JsonNode value = requiredField(json, field);
        if (!value.isTextual()) throw new IllegalArgumentException("invalid json string field: " + field);
        return value.textValue();
    }

    static long longField(String json, String field) {
        JsonNode value = requiredField(json, field);
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw new IllegalArgumentException("invalid json numeric field: " + field);
        return value.longValue();
    }

    static String objectField(String json, String field) {
        JsonNode value = requiredField(json, field);
        if (!value.isObject()) throw new IllegalArgumentException("invalid json object field: " + field);
        return value.toString();
    }

    static String scalarField(String json, String field) {
        JsonNode value = requiredField(json, field);
        if (!value.isValueNode() || value.isNull())
            throw new IllegalArgumentException("invalid json scalar field: " + field);
        return value.asText();
    }

    static String quote(String value) {
        try {
            return JSON.writeValueAsString(value == null ? "" : value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("unable to encode JSON string", impossible);
        }
    }

    private static JsonNode requiredField(String json, String field) {
        if (json == null) throw new IllegalArgumentException("invalid json document");
        try {
            JsonNode document = JSON.readTree(json);
            if (document == null || !document.isObject()) throw new IllegalArgumentException("invalid json document");
            JsonNode value = document.get(field);
            if (value == null || value.isNull()) throw new IllegalArgumentException("missing json field: " + field);
            return value;
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("invalid json document", invalid);
        }
    }
}
