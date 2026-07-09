package com.voicesecure.api;

import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.LedgerException;
import com.voicesecure.ledger.Posting;
import com.voicesecure.ledger.RepairRequest;
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
        return "POST".equals(request.method()) && "/support/repairs".equals(request.path());
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        try {
            String traceId = requiredHeader(request, "X-Trace-Id");
            RepairRequest repairRequest = new RepairRequest(
                    uuidField(request.body(), "repairId"),
                    uuidField(request.body(), "sagaId"),
                    requiredUuidHeader(request, "Idempotency-Key"),
                    ApiJson.stringField(request.body(), "currency"),
                    List.of(
                            Posting.repairDebit(uuidField(request.body(), "sourceAccountId"), ApiJson.longField(request.body(), "amount")),
                            Posting.repairCredit(uuidField(request.body(), "destinationAccountId"), ApiJson.longField(request.body(), "amount"))
                    ),
                    ApiJson.stringField(request.body(), "justification"),
                    ApiJson.stringField(request.body(), "requestedBy")
            );
            LedgerBatch batch = supportService.requestRepair(repairRequest);
            return ApiResponse.json(200, repairBody(repairRequest, batch, traceId));
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

    private static String repairBody(RepairRequest repairRequest, LedgerBatch batch, String traceId) {
        return "{"
                + "\"repairId\":" + ApiJson.quote(repairRequest.repairId().toString()) + ","
                + "\"sagaId\":" + ApiJson.quote(repairRequest.sagaId().toString()) + ","
                + "\"requestedBy\":" + ApiJson.quote(repairRequest.requestedBy()) + ","
                + "\"currency\":" + ApiJson.quote(repairRequest.currency()) + ","
                + "\"amount\":" + repairAmount(repairRequest) + ","
                + "\"entryCount\":" + batch.entries().size() + ","
                + "\"status\":\"APPLIED\","
                + "\"traceId\":" + ApiJson.quote(traceId)
                + "}";
    }

    private static long repairAmount(RepairRequest repairRequest) {
        return repairRequest.postings().stream()
                .mapToLong(posting -> Math.abs(posting.signedAmount()))
                .sum() / 2;
    }
}
