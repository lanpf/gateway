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
        assertFalse(Files.exists(root.resolve("gateway-application/src/main/java/com/cloud/gateway/application/policy/GatewayRequestAttributes.java")));
    }
}
