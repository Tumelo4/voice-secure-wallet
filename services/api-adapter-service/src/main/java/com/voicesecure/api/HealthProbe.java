package com.voicesecure.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthProbe {
    private HealthProbe() { }

    public static void main(String[] args) throws Exception {
        String port = System.getenv().getOrDefault("PORT", "8080");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health/ready"))
                .header("X-Trace-Id", "container-health-probe")
                .timeout(Duration.ofSeconds(2)).GET().build();
        int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status != 200) throw new IllegalStateException("health probe returned " + status);
    }
}
