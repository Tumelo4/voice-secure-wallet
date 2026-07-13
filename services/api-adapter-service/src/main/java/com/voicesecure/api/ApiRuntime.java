package com.voicesecure.api;

import java.time.Instant;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;

public final class ApiRuntime implements ApiEndpoint {
    private static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
    private static final String FORBIDDEN = "FORBIDDEN";
    private static final String INSUFFICIENT_SCOPE = "INSUFFICIENT_SCOPE";
    private static final String TRACE_REQUIRED = "TRACE_REQUIRED";
    private static final String RATE_LIMITED = "RATE_LIMITED";

    private final ApiEndpoint endpoint;
    private final BearerTokenVerifier tokenVerifier;
    private final ApiRateLimiter rateLimiter;
    private final ApiRequestLogSink logSink;

    public ApiRuntime(
            ApiEndpoint endpoint,
            BearerTokenVerifier tokenVerifier,
            ApiRateLimiter rateLimiter,
            ApiRequestLogSink logSink
    ) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "tokenVerifier");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.logSink = Objects.requireNonNull(logSink, "logSink");
    }

    @Override
    public boolean supports(ApiRequest request) {
        return true;
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        Objects.requireNonNull(request, "request");
        String traceId = request.header("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            return finish(request, "anonymous", "", ApiResponse.error(400, TRACE_REQUIRED, "trace id is required"));
        }

        if (endpoint.isPublic(request)) {
            return finish(request, "anonymous", traceId, endpoint.handle(request));
        }

        String authorization = request.header("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return finish(request, "anonymous", traceId, ApiResponse.error(401, AUTHENTICATION_REQUIRED, "bearer token is required"));
        }
        if (!authorization.startsWith("Bearer ")) {
            return finish(request, "anonymous", traceId, ApiResponse.error(401, AUTHENTICATION_REQUIRED, "bearer token is required"));
        }

        String token = authorization.substring("Bearer ".length()).trim();
        Optional<ApiPrincipal> principal = tokenVerifier.verify(token);
        if (principal.isEmpty()) {
            return finish(request, "anonymous", traceId, ApiResponse.error(403, FORBIDDEN, "bearer token is invalid"));
        }

        String principalId = principal.get().principalId();
        Set<String> requiredScopes = endpoint.requiredScopes(request);
        if (!principal.get().hasAnyScope(requiredScopes)) {
            return finish(
                    request,
                    principalId,
                    traceId,
                    ApiResponse.error(403, INSUFFICIENT_SCOPE, "bearer token lacks required scope for this route")
            );
        }

        if (!rateLimiter.allow(principalId)) {
            ApiResponse response = ApiResponse.error(429, RATE_LIMITED, "rate limit exceeded").withHeader("Retry-After", "2");
            return finish(request, principalId, traceId, response);
        }

        ApiRequest authenticatedRequest = request.withHeader(
                ApiSecurityContext.AUTHENTICATED_PRINCIPAL_HEADER,
                principalId
        );
        return finish(request, principalId, traceId, endpoint.handle(authenticatedRequest));
    }

    private ApiResponse finish(ApiRequest request, String principalId, String traceId, ApiResponse response) {
        logSink.record(new ApiRequestLogEntry(
                Instant.now(),
                principalId,
                traceId,
                request.method(),
                request.path(),
                response.status()
        ));
        return response;
    }
}
