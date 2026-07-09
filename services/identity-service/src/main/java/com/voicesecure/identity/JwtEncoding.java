package com.voicesecure.identity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class JwtEncoding {
    private JwtEncoding() {
    }

    static String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
