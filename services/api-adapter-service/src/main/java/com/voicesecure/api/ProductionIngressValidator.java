package com.voicesecure.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProductionIngressValidator {
    private final ProductionIngressPolicy policy;

    public ProductionIngressValidator() {
        this(ProductionIngressPolicy.defaults());
    }

    public ProductionIngressValidator(ProductionIngressPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public ProductionIngressValidationReport validate(ProductionIngressPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<String> blockers = new ArrayList<>();
        validateTransportSecurity(plan, blockers);
        validateRuntimeControls(plan, blockers);
        validatePublicPaths(plan, blockers);
        return new ProductionIngressValidationReport(blockers.isEmpty(), blockers);
    }

    private void validateTransportSecurity(ProductionIngressPlan plan, List<String> blockers) {
        if (!plan.tlsTerminatedAtEdge()) {
            blockers.add("production ingress must terminate TLS");
        }
        if (!plan.minimumTlsVersion().isAtLeast(policy.minimumTlsVersion())) {
            blockers.add("production ingress must require " + policy.minimumTlsVersion().displayName() + " or newer");
        }
        if (!plan.mutualTlsRequired()) {
            blockers.add("production ingress must require mutual TLS");
        }
        if (!plan.clientCertificateForwarded()) {
            blockers.add("mTLS client certificate identity must be forwarded to the runtime");
        }
    }

    private void validateRuntimeControls(ProductionIngressPlan plan, List<String> blockers) {
        if (!plan.oidcJwksConfigured()) {
            blockers.add("OIDC/JWKS provider must be configured");
        }
        if (!plan.distributedRateLimitStore()) {
            blockers.add("rate limits must use a distributed store");
        }
        if (!plan.wafEnabled()) {
            blockers.add("WAF must be enabled before public ingress");
        }
        if (!plan.hstsEnabled()) {
            blockers.add("HSTS must be enabled at the edge");
        }
        if (!plan.traceHeaderForwarding()) {
            blockers.add("trace headers must be forwarded from ingress to runtime");
        }
        if (!plan.requestBodyLimitEnabled() || plan.maxRequestBodyKb() <= 0 || plan.maxRequestBodyKb() > policy.maxRequestBodyKb()) {
            blockers.add("request body limit must be at most " + policy.maxRequestBodyKb() + " KB");
        }
    }

    private void validatePublicPaths(ProductionIngressPlan plan, List<String> blockers) {
        if (!plan.publicPaths().equals(policy.publicHealthPaths())) {
            blockers.add("public health paths must expose /health/live and /health/ready only");
        }
        if (plan.publicPaths().stream().anyMatch(path -> path.equals("/admin") || path.startsWith("/admin/"))) {
            blockers.add("admin routes must not be publicly exposed");
        }
    }
}
