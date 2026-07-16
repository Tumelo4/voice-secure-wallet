package com.voicesecure.api;

import com.voicesecure.beneficiaries.Beneficiary;
import com.voicesecure.beneficiaries.BeneficiaryException;
import com.voicesecure.beneficiaries.BeneficiaryService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class BeneficiaryApiAdapter implements ApiEndpoint {
    private static final String PATH = "/v1/me/beneficiaries";
    private static final Set<String> REQUIRED_SCOPES = Set.of("wallet:beneficiary");
    private final BeneficiaryService beneficiaryService;
    private final BeneficiaryAccountDirectory accountDirectory;

    public BeneficiaryApiAdapter(BeneficiaryService beneficiaryService, BeneficiaryAccountDirectory accountDirectory) {
        this.beneficiaryService = Objects.requireNonNull(beneficiaryService, "beneficiaryService");
        this.accountDirectory = Objects.requireNonNull(accountDirectory, "accountDirectory");
    }

    public boolean supports(ApiRequest request) {
        return PATH.equals(request.path()) && ("GET".equals(request.method()) || "POST".equals(request.method()));
    }

    public ApiResponse handle(ApiRequest request) {
        try {
            UUID customerId = UUID.fromString(ApiSecurityContext.requirePrincipal(request));
            if ("GET".equals(request.method())) {
                return ApiResponse.json(200, listBody(beneficiaryService.forCustomer(customerId)));
            }
            String accountNumber = ApiJson.stringField(request.body(), "accountNumber");
            BeneficiaryAccountDirectory.ResolvedBeneficiaryAccount resolved = accountDirectory.resolve(
                    ApiJson.stringField(request.body(), "bankCode"), accountNumber);
            if (resolved == null || !resolved.verified()) {
                return ApiResponse.error(422, "BENEFICIARY_VERIFICATION_FAILED", "We could not verify this beneficiary.");
            }
            Beneficiary created = beneficiaryService.create(
                    customerId, resolved.destinationAccountId(),
                    ApiJson.stringField(request.body(), "displayName"), accountNumber, resolved.currency());
            return ApiResponse.json(201, beneficiaryBody(created));
        } catch (BeneficiaryException exception) {
            if (exception.getMessage().contains("already exists")) {
                return ApiResponse.error(409, "BENEFICIARY_ALREADY_EXISTS", "This beneficiary already exists.");
            }
            return ApiResponse.error(400, "BENEFICIARY_INVALID", "Review the beneficiary details and try again.");
        } catch (SecurityException exception) {
            return ApiResponse.error(401, "AUTHENTICATION_REQUIRED", "Authentication is required.");
        } catch (IllegalStateException exception) {
            return ApiResponse.error(503, "BENEFICIARY_DIRECTORY_UNAVAILABLE", "Beneficiary verification is temporarily unavailable.");
        } catch (IllegalArgumentException exception) {
            return ApiResponse.error(400, "BENEFICIARY_INVALID", "Review the beneficiary details and try again.");
        }
    }

    public Set<String> requiredScopes(ApiRequest request) {
        return supports(request) ? REQUIRED_SCOPES : Set.of();
    }

    private static String listBody(List<Beneficiary> beneficiaries) {
        StringBuilder result = new StringBuilder("{\"beneficiaries\":[");
        for (int index = 0; index < beneficiaries.size(); index++) {
            if (index > 0) result.append(',');
            result.append(beneficiaryBody(beneficiaries.get(index)));
        }
        return result.append("]}").toString();
    }

    private static String beneficiaryBody(Beneficiary beneficiary) {
        return "{"
                + "\"beneficiaryId\":" + ApiJson.quote(beneficiary.beneficiaryId().toString()) + ","
                + "\"displayName\":" + ApiJson.quote(beneficiary.displayName()) + ","
                + "\"maskedAccountNumber\":" + ApiJson.quote(beneficiary.maskedAccountNumber()) + ","
                + "\"currency\":" + ApiJson.quote(beneficiary.currency()) + ","
                + "\"status\":" + ApiJson.quote(beneficiary.status().name()) + ","
                + "\"availableAt\":" + ApiJson.quote(beneficiary.availableAt().toString())
                + "}";
    }
}
