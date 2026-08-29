package org.example.billing.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BillingContractPurityTest {
    @Test
    void contractSourceDoesNotImportFrameworkTransportOrPersistence() throws Exception {
        Path root = Path.of("src/main/java");
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    assertFalse(source.contains("org.springframework"), path.toString());
                    assertFalse(source.contains("com.fasterxml.jackson"), path.toString());
                    assertFalse(source.contains("java.net.http"), path.toString());
                    assertFalse(source.contains("java.sql"), path.toString());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }
}
