package com.devchesterunited.devchesterunitedv2.diagnostico;

import com.devchesterunited.devchesterunitedv2.service.diagnostico.WhoscoredPlayerIdentityDiagnosticService;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/** Manual entry point; it is deliberately outside the normal test source set. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WhoScoredPlayerIdentityDiagnosticManualConfiguration.class)
@TestPropertySource(locations = "classpath:application.properties")
class GenerateWhoScoredPlayerIdentityDiagnosticTest {
    @Autowired private WhoscoredPlayerIdentityDiagnosticService service;

    @Test
    void generateDiagnostic() throws IOException {
        var result = service.generar(Path.of("build/reports/whoscored-player-identity-diagnostic.csv"));
        System.out.println("jugadores observados: " + result.observedPlayers());
        System.out.println("jugadores no resueltos: " + result.unresolvedPlayers());
        System.out.println("identidades WHOSCORED persistidas: " + result.persistedPlayers());
        System.out.println("CSV: " + result.output().toAbsolutePath());
    }
}
