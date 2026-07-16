package com.voicesecure.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public final class HttpBeneficiaryAccountDirectory implements BeneficiaryAccountDirectory {
    private final HttpClient client;
    private final URI baseUri;
    private final String serviceToken;
    private final Duration timeout;
    public HttpBeneficiaryAccountDirectory(URI baseUri, String serviceToken, Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.baseUri = URI.create(baseUri.toString().replaceAll("/+$", "") + "/");
        this.serviceToken = serviceToken;
        this.timeout = timeout;
    }
    @Override public ResolvedBeneficiaryAccount resolve(String bankCode, String accountNumber) {
        String query = "v1/accounts/resolve?bankCode=" + encode(bankCode) + "&accountNumber=" + encode(accountNumber);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(query)).timeout(timeout)
                .header("X-Service-Token", serviceToken).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            return new ResolvedBeneficiaryAccount(UUID.fromString(ApiJson.stringField(response.body(), "destinationAccountId")),
                    ApiJson.stringField(response.body(), "currency"),
                    Boolean.parseBoolean(ApiJson.scalarField(response.body(), "verified")));
        } catch (IOException failure) { throw unavailable(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw unavailable(); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static IllegalStateException unavailable() { return new IllegalStateException("beneficiary directory unavailable"); }
}
