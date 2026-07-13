package com.voicesecure.api;

import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.LedgerException;
import com.voicesecure.ledger.Posting;
import com.voicesecure.support.PendingRepair;
import com.voicesecure.support.SupportException;
import com.voicesecure.support.SupportService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class SupportRepairApiAdapter implements ApiEndpoint {
    private static final Set<String> REQUIRED_SCOPES = Set.of("support:repair");

    private final SupportService supportService;

    public SupportRepairApiAdapter(SupportService supportService) {
        this.supportService = Objects.requireNonNull(supportService, "supportService");
    }

    @Override
    public boolean supports(ApiRequest request) {
        return "POST".equals(request.method()) && (
                "/support/repairs".equals(request.path()) || approvalRepairId(request.path()) != null);
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        try {
            String principal = ApiSecurityContext.requirePrincipal(request);
            UUID approvalId = approvalRepairId(request.path());
            if (approvalId != null) {
                LedgerBatch batch = supportService.approveRepair(
                        approvalId, requiredUuidHeader(request, "Idempotency-Key"), principal);
                return ApiResponse.json(200, appliedBody(approvalId, batch));
            }
            long amount = ApiJson.longField(request.body(), "amount");
            PendingRepair pending = supportService.requestRepair(
                    uuidField(request.body(), "repairId"), uuidField(request.body(), "sagaId"),
                    ApiJson.stringField(request.body(), "currency"),
                    List.of(
                            Posting.repairDebit(uuidField(request.body(), "sourceAccountId"), amount),
                            Posting.repairCredit(uuidField(request.body(), "destinationAccountId"), amount)),
                    ApiJson.stringField(request.body(), "justification"), principal);
            return ApiResponse.json(202, pendingBody(pending));
        } catch (LedgerException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("idempotency key reused")) {
                return ApiResponse.error(409, "IDEMPOTENCY_CONFLICT", ex.getMessage());
            }
            return ApiResponse.error(400, "VALIDATION_FAILED", ex.getMessage());
        } catch (SupportException | IllegalArgumentException ex) {
            return ApiResponse.error(400, "VALIDATION_FAILED", ex.getMessage());
        }
    }

    @Override
    public Set<String> requiredScopes(ApiRequest request) {
        return supports(request) ? REQUIRED_SCOPES : Set.of();
    }

    private static UUID uuidField(String json, String field) {
        return UUID.fromString(ApiJson.stringField(json, field));
    }

    private static UUID requiredUuidHeader(ApiRequest request, String name) {
        return UUID.fromString(requiredHeader(request, name));
    }

    private static String requiredHeader(ApiRequest request, String name) {
        String value = request.header(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required header: " + name);
        }
        return value;
    }

    private static String pendingBody(PendingRepair repair) {
        return "{"
                + "\"repairId\":" + ApiJson.quote(repair.repairId().toString()) + ","
                + "\"status\":\"PENDING_APPROVAL\""
                + "}";
    }

    private static String appliedBody(UUID repairId, LedgerBatch batch) {
        return "{"
                + "\"repairId\":" + ApiJson.quote(repairId.toString()) + ","
                + "\"entryCount\":" + batch.entries().size() + ","
                + "\"status\":\"APPLIED\""
                + "}";
    }

    private static UUID approvalRepairId(String path) {
        String prefix = "/support/repairs/";
        String suffix = "/approve";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) return null;
        String value = path.substring(prefix.length(), path.length() - suffix.length());
        if (value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException exception) { return null; }
    }
}
