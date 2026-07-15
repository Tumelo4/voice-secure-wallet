package com.voicesecure.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Production-capable Jetty/Javalin transport around the stable ApiEndpoint port. */
public final class ApiHttpServer implements AutoCloseable {
    private final Javalin server;

    private ApiHttpServer(Javalin server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static ApiHttpServer start(ApiEndpoint endpoint) throws IOException {
        return start(new InetSocketAddress("127.0.0.1", 0), endpoint);
    }

    public static ApiHttpServer start(InetSocketAddress address, ApiEndpoint endpoint) throws IOException {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(endpoint, "endpoint");
        try {
            Javalin app = Javalin.create(config -> {
                config.http.maxRequestSize = 262_144L;
                config.startup.showJavalinBanner = false;
                Handler handler = context -> dispatch(context, endpoint);
                config.routes.get("/*", handler);
                config.routes.post("/*", handler);
                config.routes.put("/*", handler);
                config.routes.patch("/*", handler);
                config.routes.delete("/*", handler);
            });
            app.start(address.getHostString(), address.getPort());
            return new ApiHttpServer(app);
        } catch (RuntimeException exception) {
            throw new IOException("unable to start API HTTP server", exception);
        }
    }

    public URI uri(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create("http://127.0.0.1:" + server.port() + normalizedPath);
    }

    @Override
    public void close() {
        server.stop();
    }

    private static void dispatch(Context context, ApiEndpoint endpoint) {
        ApiResponse response;
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            context.headerMap().forEach(headers::put);
            response = endpoint.handle(new ApiRequest(
                    context.method().name(), context.path(), headers, context.body()));
        } catch (RuntimeException error) {
            response = ApiResponse.error(500, "INTERNAL_SERVER_ERROR", "internal server error");
        }
        response.headers().forEach(context::header);
        context.status(response.status()).result(response.body());
    }
}
