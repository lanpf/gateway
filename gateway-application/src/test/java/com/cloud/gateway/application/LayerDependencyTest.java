package com.cloud.gateway.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-level dependency guard without introducing another architecture-test dependency. */
class LayerDependencyTest {
    @Test
    void shouldKeepCapabilityConfigurationAndPureRulesIndependentOfRuntime() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("gateway-domain"))) {
            root = root.getParent();
        }
        Path capability = root.resolve("gateway-domain/src/main/java/com/cloud/gateway/domain/capability");
        try (Stream<Path> files = Files.walk(capability.resolve("runtime"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(file).contains("com.cloud.gateway.domain.capability.policy"),
                        () -> file + " must not depend on effective configuration");
            }
        }
        for (String boundary : List.of("policy", "ratelimit", "resilience", "header")) {
            try (Stream<Path> files = Files.walk(capability.resolve(boundary))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("com.cloud.gateway.domain.capability.runtime"),
                            () -> file + " must not depend on runtime");
                    if (boundary.equals("policy")) {
                        assertFalse(source.contains("com.cloud.gateway.domain.ingress.snapshot"),
                                () -> file + " must not depend on its enclosing snapshot");
                    }
                }
            }
        }
    }

    @Test
    void shouldKeepInnerLayersIndependentOfProtocolAndImplementationImports() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("gateway-application"))) {
            root = root.getParent();
        }
        for (String module : List.of("gateway-domain", "gateway-application")) {
            Path sources = root.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(sources));
            try (Stream<Path> files = Files.walk(sources)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file)) {
                        if (line.startsWith("import ")) {
                            assertFalse(line.matches("import (static )?(org\\.springframework|reactor|"
                                    + "com\\.cloud\\.gateway\\.(interfaces|infrastructure|api))\\..*"),
                                    () -> file + ": " + line);
                        }
                    }
                }
            }
        }
        assertFalse(Files.exists(root.resolve("gateway-application/src/main/java/com/cloud/gateway/application/access/GatewayRequestAttributes.java")));
    }
}
