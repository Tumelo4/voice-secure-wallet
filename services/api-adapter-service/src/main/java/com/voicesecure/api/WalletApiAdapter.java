package com.voicesecure.api;

import com.voicesecure.wallet.WalletBalance;
import com.voicesecure.wallet.WalletException;
import com.voicesecure.wallet.WalletService;
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
        return "GET".equals(request.method()) && accountIdPath(request.path()) != null;
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        try {
            WalletBalance balance = walletService.balance(UUID.fromString(accountIdPath(request.path())));
            return ApiResponse.json(200, balanceBody(balance));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, "VALIDATION_FAILED", ex.getMessage());
        } catch (WalletException ex) {
            return ApiResponse.error(404, "WALLET_NOT_FOUND", ex.getMessage());
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
}
