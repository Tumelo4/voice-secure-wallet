package com.voicesecure.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArchitectureBoundaryTests {
    private static final Set<String> DOMAIN_MODULES = Set.of(
            "beneficiary-service", "compliance-service", "fraud-service", "ledger-service",
            "notification-service", "payment-service", "recovery-service", "support-service", "wallet-service"
    );
    private static final List<String> FORBIDDEN_DOMAIN_IMPORTS = List.of(
            "org.springframework", "jakarta.ws.rs", "javax.ws.rs", "org.apache.kafka",
            "software.amazon.awssdk", "com.amazonaws", "java.net.http", "com.sun.net.httpserver"
    );

    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        assertDomainDoesNotImportInfrastructure(root);
        assertOnlyRuntimeModuleOwnsHttpServer(root);
        assertPolicyValidatorsAreNotRuntimeServices(root);
        System.out.println("Architecture boundary tests passed: 3");
    }

    private static void assertDomainDoesNotImportInfrastructure(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        for (String module : DOMAIN_MODULES) {
            Path source = root.resolve("services").resolve(module).resolve("src/main/java");
            if (!Files.exists(source)) continue;
            try (var paths = Files.walk(source)) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(path);
                    for (String forbidden : FORBIDDEN_DOMAIN_IMPORTS) {
                        if (content.contains("import " + forbidden)) violations.add(root.relativize(path) + " imports " + forbidden);
                    }
                }
            }
        }
        if (!violations.isEmpty()) throw new AssertionError("domain/infrastructure boundary violations: " + violations);
    }

    private static void assertOnlyRuntimeModuleOwnsHttpServer(Path root) throws IOException {
        List<Path> violations = new ArrayList<>();
        try (var paths = Files.walk(root.resolve("services"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                if (path.toString().contains("api-adapter-service")) continue;
                if (Files.readString(path).contains("HttpServer.create")) violations.add(root.relativize(path));
            }
        }
        if (!violations.isEmpty()) throw new AssertionError("unexpected runtime entry points: " + violations);
    }

    private static void assertPolicyValidatorsAreNotRuntimeServices(Path root) throws IOException {
        for (String module : List.of("ops-service", "launch-service")) {
            Path source = root.resolve("services").resolve(module).resolve("src/main/java");
            try (var paths = Files.walk(source)) {
                boolean runtime = paths.filter(value -> value.toString().endsWith(".java"))
                        .anyMatch(path -> uncheckedRead(path).contains("public static void main"));
                if (runtime) throw new AssertionError(module + " must remain a build-time policy validator");
            }
        }
    }

    private static String uncheckedRead(Path path) {
        try { return Files.readString(path); } catch (IOException exception) { throw new IllegalStateException(exception); }
    }
}
