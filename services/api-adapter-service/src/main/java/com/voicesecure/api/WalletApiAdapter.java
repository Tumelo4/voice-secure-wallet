package com.voicesecure.api;

import com.voicesecure.wallet.WalletBalance;
import com.voicesecure.wallet.WalletException;
import com.voicesecure.wallet.WalletService;
import com.voicesecure.wallet.WalletAccount;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

public final class WalletApiAdapter implements ApiEndpoint {
    private static final Set<String> REQUIRED_SCOPES = Set.of("wallet:balance");

    private final WalletService walletService;

    public WalletApiAdapter(WalletService walletService) {
        this.walletService = Objects.requireNonNull(walletService, "walletService");
    }

    @Override
    public boolean supports(ApiRequest request) {
        return "GET".equals(request.method())
                && ("/v1/me/accounts".equals(request.path()) || accountIdPath(request.path()) != null);
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        try {
            UUID userId = UUID.fromString(ApiSecurityContext.requirePrincipal(request));
            if ("/v1/me/accounts".equals(request.path())) {
                return ApiResponse.json(200, accountsBody(walletService.accountsForUser(userId)));
            }
            WalletBalance balance = walletService.balanceForUser(userId, UUID.fromString(accountIdPath(request.path())));
            return ApiResponse.json(200, balanceBody(balance));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, "VALIDATION_FAILED", ex.getMessage());
        } catch (WalletException ex) {
            return ApiResponse.error(404, "WALLET_NOT_FOUND", "Account balance is unavailable.");
        } catch (SecurityException ex) {
            return ApiResponse.error(401, "AUTHENTICATION_REQUIRED", "Authentication is required.");
        }
    }

    @Override
    public Set<String> requiredScopes(ApiRequest request) {
        return supports(request) ? REQUIRED_SCOPES : Set.of();
    }

    private static String accountIdPath(String path) {
        if (!path.startsWith("/wallets/") || !path.endsWith("/balance")) {
            return null;
        }
        String accountId = path.substring("/wallets/".length(), path.length() - "/balance".length());
        return accountId.isBlank() ? null : accountId;
    }

    private static String balanceBody(WalletBalance balance) {
        return "{"
                + "\"accountId\":" + ApiJson.quote(balance.accountId().toString()) + ","
                + "\"currency\":" + ApiJson.quote(balance.currency()) + ","
                + "\"balance\":" + balance.balance() + ","
                + "\"version\":" + balance.version() + ","
                + "\"updatedAt\":" + ApiJson.quote(balance.updatedAt().toString())
                + "}";
    }

    private static String accountsBody(List<WalletAccount> accounts) {
        StringBuilder json = new StringBuilder("{\"accounts\":[");
        for (int index = 0; index < accounts.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            WalletAccount account = accounts.get(index);
            json.append('{')
                    .append("\"accountId\":").append(ApiJson.quote(account.accountId().toString())).append(',')
                    .append("\"displayName\":").append(ApiJson.quote(account.displayName())).append(',')
                    .append("\"maskedAccountNumber\":").append(ApiJson.quote(maskedAccountNumber(account.accountId()))).append(',')
                    .append("\"currency\":").append(ApiJson.quote(account.currency()))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    private static String maskedAccountNumber(UUID accountId) {
        String compact = accountId.toString().replace("-", "");
        return "•••• " + compact.substring(compact.length() - 4).toUpperCase(java.util.Locale.ROOT);
    }
}
