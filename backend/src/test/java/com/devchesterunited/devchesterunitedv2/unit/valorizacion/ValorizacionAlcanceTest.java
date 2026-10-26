package com.devchesterunited.devchesterunitedv2.service;
import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionScheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ValorizacionAlcanceTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/devchesterunited/devchesterunitedv2");

    @Test
    void valuationCodeDoesNotDependOnTradingOrAdministrativeFeatures() throws IOException {
        Path valuationRoot = SOURCE_ROOT.resolve("modelo/valorizacion");
        Path valuationService = SOURCE_ROOT.resolve("service");
        Path valuationConfiguration = SOURCE_ROOT.resolve("configuration/ValorizacionScheduler.java");

        try (Stream<Path> modelFiles = javaFiles(valuationRoot);
             Stream<Path> serviceFiles = javaFiles(valuationService);
             Stream<Path> configurationFiles = Stream.of(valuationConfiguration)) {
            Stream.concat(Stream.concat(modelFiles, serviceFiles), configurationFiles)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains("Valorizacion")
                            || path.toString().contains("modelo/valorizacion"))
                    .forEach(this::assertNoForbiddenReference);
        }
    }

    private Stream<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        return Files.walk(root).filter(path -> path.toString().endsWith(".java"));
    }

    private void assertNoForbiddenReference(Path path) {
        try {
            String source = Files.readString(path).toLowerCase(Locale.ROOT);
            String[] forbidden = {"token", "wallet", "portfolio", "balance", "compra", "venta", "admin",
                    "preauthorize", "role.admin"};
            for (String term : forbidden) {
                assertFalse(source.contains(term), () -> path + " contains forbidden scope reference: " + term);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect valuation source " + path, exception);
        }
    }

}
