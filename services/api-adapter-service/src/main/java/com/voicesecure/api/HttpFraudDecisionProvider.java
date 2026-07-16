package com.voicesecure.api;

import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.PaymentRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class HttpFraudDecisionProvider implements FraudDecisionProvider {
    private final HttpClient client;
    private final URI endpoint;
    private final String serviceToken;
    private final Duration timeout;

    public HttpFraudDecisionProvider(URI baseUri, String serviceToken, Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.endpoint = URI.create(baseUri.toString().replaceAll("/+$", "") + "/v1/fraud/assessments");
        this.serviceToken = requireText(serviceToken, "fraud service token");
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Override
    public FraudDecision assess(PaymentRequest value) {
        String body = "{" +
                "\"paymentId\":" + ApiJson.quote(value.sagaId().toString()) + "," +
                "\"customerId\":" + ApiJson.quote(value.userId().toString()) + "," +
                "\"sourceAccountId\":" + ApiJson.quote(value.fromAccountId().toString()) + "," +
                "\"beneficiaryAccountId\":" + ApiJson.quote(value.toAccountId().toString()) + "," +
                "\"amountMinor\":" + value.amount() + "," +
                "\"currency\":" + ApiJson.quote(value.currency()) + "}";
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json").header("X-Service-Token", serviceToken)
                .header("Idempotency-Key", value.sagaId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            return new FraudDecision(Double.parseDouble(ApiJson.scalarField(response.body(), "score")),
                    AuthPolicy.valueOf(ApiJson.stringField(response.body(), "authPolicy")),
                    Boolean.parseBoolean(ApiJson.scalarField(response.body(), "approved")),
                    ApiJson.stringField(response.body(), "reason"));
        } catch (IOException failure) {
            throw unavailable();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() { return new IllegalStateException("fraud service unavailable"); }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
