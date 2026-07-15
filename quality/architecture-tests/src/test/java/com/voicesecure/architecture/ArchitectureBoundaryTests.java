package com.voicesecure.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        assertLedgerLayeringIsStructural(root);
        assertProductionRuntimeDoesNotUseJdkHttpServer(root);
        assertPolicyValidatorsAreNotRuntimeServices(root);
        System.out.println("Architecture boundary tests passed: 4");
    }

    private static void assertLedgerLayeringIsStructural(Path root) throws IOException {
        Path source = root.resolve("services/ledger-service/src/main/java/com/voicesecure/ledger");
        Map<String, String> requiredLocations = Map.of(
                "domain/LedgerRepository.java", "package com.voicesecure.ledger.domain;",
                "application/LedgerService.java", "package com.voicesecure.ledger.application;",
                "infrastructure/PostgresLedgerRepository.java", "package com.voicesecure.ledger.infrastructure;",
                "infrastructure/InMemoryLedgerRepository.java", "package com.voicesecure.ledger.infrastructure;");
        List<String> violations = new ArrayList<>();
        requiredLocations.forEach((relative, declaration) -> {
            Path path = source.resolve(relative);
            if (!Files.exists(path) || !uncheckedRead(path).contains(declaration)) violations.add(relative);
        });
        try (var paths = Files.walk(source.resolve("domain"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String content = Files.readString(path);
                if (content.contains("java.sql") || content.contains("javax.sql")
                        || content.contains(".infrastructure")) {
                    violations.add(root.relativize(path).toString());
                }
            }
        }
        if (!violations.isEmpty()) throw new AssertionError("ledger layering violations: " + violations);
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

    private static void assertProductionRuntimeDoesNotUseJdkHttpServer(Path root) throws IOException {
        List<Path> violations = new ArrayList<>();
        try (var paths = Files.walk(root.resolve("services"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String content = Files.readString(path);
                if (content.contains("com.sun.net.httpserver") || content.contains("HttpServer.create")) {
                    violations.add(root.relativize(path));
                }
            }
        }
        if (!violations.isEmpty()) throw new AssertionError("JDK development HTTP server is forbidden: " + violations);
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
