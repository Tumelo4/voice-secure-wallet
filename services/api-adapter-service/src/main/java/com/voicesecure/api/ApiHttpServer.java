package com.voicesecure.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ApiHttpServer implements AutoCloseable {
    private final HttpServer server;

    private ApiHttpServer(HttpServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static ApiHttpServer start(ApiEndpoint endpoint) throws IOException {
        return start(new InetSocketAddress("127.0.0.1", 0), endpoint);
    }

    public static ApiHttpServer start(InetSocketAddress address, ApiEndpoint endpoint) throws IOException {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(endpoint, "endpoint");
        HttpServer server = HttpServer.create(address, 0);
        server.createContext("/", exchange -> handle(exchange, endpoint));
        server.start();
        return new ApiHttpServer(server);
    }

    public URI uri(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + normalizedPath);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange, ApiEndpoint endpoint) throws IOException {
        ApiResponse response;
        try {
            response = endpoint.handle(new ApiRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    requestHeaders(exchange.getRequestHeaders()),
                    readBody(exchange.getRequestBody())
            ));
        } catch (RuntimeException error) {
            response = ApiResponse.error(500, "INTERNAL_SERVER_ERROR", "internal server error");
        }
        writeResponse(exchange, response);
    }

    private static Map<String, String> requestHeaders(Headers headers) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                values.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return values;
    }

    private static String readBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeResponse(HttpExchange exchange, ApiResponse response) throws IOException {
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            exchange.getResponseHeaders().set(header.getKey(), header.getValue());
        }
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        } finally {
            exchange.close();
        }
    }
}
