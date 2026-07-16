package com.voicesecure.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class HttpVoiceGatewayClient implements VoiceGatewayClient {
    private final HttpClient client;
    private final URI baseUri;
    private final String serviceToken;
    private final Duration timeout;

    public HttpVoiceGatewayClient(URI baseUri, String serviceToken, Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), baseUri, serviceToken, timeout);
    }

    HttpVoiceGatewayClient(HttpClient client, URI baseUri, String serviceToken, Duration timeout) {
        this.client = Objects.requireNonNull(client);
        Objects.requireNonNull(baseUri);
        this.baseUri = URI.create(baseUri.toString().replaceAll("/+$", "") + "/");
        this.serviceToken = requireText(serviceToken, "voice service token");
        this.timeout = Objects.requireNonNull(timeout);
        if (!this.baseUri.isAbsolute() || this.baseUri.getHost() == null || !("https".equals(this.baseUri.getScheme()) || "http".equals(this.baseUri.getScheme()))) {
            throw new IllegalArgumentException("voice service URI must be HTTP(S)");
        }
    }

    @Override
    public Challenge issueChallenge(UUID customerId, String phrase, String transactionBindingHash) {
        String body = "{" +
                "\"userId\":" + ApiJson.quote(customerId.toString()) + "," +
                "\"phrase\":" + ApiJson.quote(phrase) + "," +
                "\"ttlSeconds\":30," +
                "\"transactionBindingHash\":" + ApiJson.quote(transactionBindingHash) + "}";
        String response = post("/v1/voice/challenges", body, null);
        return new Challenge(UUID.fromString(ApiJson.stringField(response, "challengeId")),
                ApiJson.stringField(response, "phrase"), Instant.parse(ApiJson.stringField(response, "expiresAt")));
    }

    @Override
    public String verify(Verification request) {
        String body = "{" +
                "\"userId\":" + ApiJson.quote(request.customerId().toString()) + "," +
                "\"challengeId\":" + ApiJson.quote(request.challengeId().toString()) + "," +
                "\"paymentReference\":" + ApiJson.quote(request.paymentReference()) + "," +
                "\"transactionBindingHash\":" + ApiJson.quote(request.transactionBindingHash()) + "," +
                "\"authPolicy\":" + ApiJson.quote(request.authPolicy()) + "," +
                "\"transactionAmountMinor\":" + request.transactionAmountMinor() + "," +
                "\"capturedAt\":" + ApiJson.quote(request.capturedAt().toString()) + "," +
                "\"audio\":" + request.audioJson() + "}";
        return post("/v1/voice/verifications", body, request.idempotencyKey());
    }

    private String post(String path, String body, String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path.substring(1))).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Service-Token", serviceToken);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        try {
            HttpResponse<String> response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("voice service request failed with status " + response.statusCode());
            }
            return response.body();
        } catch (IOException error) {
            throw new IllegalStateException("voice service request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("voice service request interrupted", error);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
