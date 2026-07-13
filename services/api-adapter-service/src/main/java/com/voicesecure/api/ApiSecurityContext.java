package com.voicesecure.api;

final class ApiSecurityContext {
    static final String AUTHENTICATED_PRINCIPAL_HEADER = "X-VoiceSecure-Authenticated-Principal";

    private ApiSecurityContext() {
    }

    static String requirePrincipal(ApiRequest request) {
        String principalId = request.header(AUTHENTICATED_PRINCIPAL_HEADER);
        if (principalId == null || principalId.isBlank()) {
            throw new SecurityException("authenticated principal is required");
        }
        return principalId.trim();
    }
}
