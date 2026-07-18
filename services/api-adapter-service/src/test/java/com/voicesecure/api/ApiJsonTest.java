package com.voicesecure.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ApiJsonTest {
    @Test
    void parsesWhitespaceEscapesAndNestedObjects() {
        String json = "{ \"message\" : \"line one\\n\\\"quoted\\\"\", \"amount\" : 42, \"nested\" : { \"ok\" : true } }";

        assertEquals("line one\n\"quoted\"", ApiJson.stringField(json, "message"));
        assertEquals(42L, ApiJson.longField(json, "amount"));
        assertEquals("{\"ok\":true}", ApiJson.objectField(json, "nested"));
        assertEquals("true", ApiJson.scalarField(ApiJson.objectField(json, "nested"), "ok"));
    }

    @Test
    void rejectsMalformedDocumentsAndIncorrectFieldTypes() {
        assertThrows(IllegalArgumentException.class, () -> ApiJson.stringField("{\"value\":1}", "value"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.longField("{\"value\":1.5}", "value"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.stringField("not-json", "value"));
        assertEquals("\"a\\n\\\"b\"", ApiJson.quote("a\n\"b"));
    }
}
