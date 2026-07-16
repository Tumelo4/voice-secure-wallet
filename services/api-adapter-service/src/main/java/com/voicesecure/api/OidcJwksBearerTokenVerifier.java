package com.voicesecure.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OidcJwksBearerTokenVerifier implements BearerTokenVerifier {
    private final DefaultJWTProcessor<SecurityContext> processor;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public OidcJwksBearerTokenVerifier(URI jwksUri, URI issuer, String audience, Clock clock) {
        this(remoteSource(jwksUri), issuer, audience, clock);
    }

    OidcJwksBearerTokenVerifier(JWKSource<SecurityContext> source, URI issuer, String audience, Clock clock) {
        this.issuer = requireHttpsIssuer(issuer);
        this.audience = requireText(audience, "audience");
        this.clock = Objects.requireNonNull(clock, "clock");
        processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
                Objects.requireNonNull(source, "source")));
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
    }

    @Override
    public Optional<ApiPrincipal> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            JWTClaimsSet claims = processor.process(token, null);
            Instant now = clock.instant();
            if (!issuer.equals(claims.getIssuer()) || !claims.getAudience().contains(audience)) return Optional.empty();
            if (claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(now)) return Optional.empty();
            if (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().toInstant().isAfter(now)) return Optional.empty();
            String subject = claims.getSubject();
            UUID.fromString(subject);
            return Optional.of(ApiPrincipal.fromScopeString(subject, claims.getStringClaim("scope")));
        } catch (ParseException | com.nimbusds.jose.JOSEException |
                 com.nimbusds.jose.proc.BadJOSEException | RuntimeException invalid) {
            return Optional.empty();
        }
    }

    private static JWKSource<SecurityContext> remoteSource(URI uri) {
        Objects.requireNonNull(uri, "jwksUri");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("JWKS URI must use https://");
        }
        try { return JWKSourceBuilder.<SecurityContext>create(uri.toURL()).retrying(true).build(); }
        catch (java.net.MalformedURLException invalid) { throw new IllegalArgumentException("invalid JWKS URI", invalid); }
    }
    private static String requireHttpsIssuer(URI value) {
        Objects.requireNonNull(value, "issuer");
        if (!"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null) {
            throw new IllegalArgumentException("issuer must use https://");
        }
        return value.toString();
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
