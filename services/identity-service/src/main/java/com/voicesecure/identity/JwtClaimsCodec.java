package com.voicesecure.identity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class JwtClaimsCodec {
    String write(AccessTokenClaims claims) {
        return "{"
                + "\"sub\":" + JwtJson.quote(claims.subjectUserId().toString()) + ","
                + "\"device_id\":" + JwtJson.quote(claims.deviceId().toString()) + ","
                + "\"family_id\":" + JwtJson.quote(claims.familyId().toString()) + ","
                + "\"scope\":" + JwtJson.quote(claims.scope()) + ","
                + "\"jti\":" + JwtJson.quote(claims.tokenId().toString()) + ","
                + "\"iat\":" + JwtJson.quote(claims.issuedAt().toString()) + ","
                + "\"exp\":" + JwtJson.quote(claims.expiresAt().toString())
                + "}";
    }

    AccessTokenClaims read(String json) {
        Map<String, String> values = JwtJson.read(json);
        return new AccessTokenClaims(
                UUID.fromString(required(values, "sub")),
                UUID.fromString(required(values, "device_id")),
                UUID.fromString(required(values, "family_id")),
                required(values, "scope"),
                UUID.fromString(required(values, "jti")),
                Instant.parse(required(values, "iat")),
                Instant.parse(required(values, "exp"))
        );
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IdentityException("missing claim: " + key);
        }
        return value;
    }
}
