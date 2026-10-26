package com.devchesterunited.devchesterunitedv2.diagnostico;

import com.devchesterunited.devchesterunitedv2.service.diagnostico.WhoscoredTeamEnrollmentReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;

/** Entry point manual; no pertenece a la suite normal de tests. */
@SpringBootTest(
        classes = WhoScoredTeamEnrollmentReportManualConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GenerateWhoScoredTeamEnrollmentReportTest {

    @Autowired
    private WhoscoredTeamEnrollmentReportService service;

    @Test
    void generateReport() throws IOException {
        var result = service.generar(Path.of("build/reports/whoscored-team-enrollment.csv"));
        System.out.println("teamId únicos: " + result.totalTeams());
        result.byCompetition().forEach((competition, count) ->
                System.out.println("  " + competition + ": " + count));
        System.out.println("sin candidatos locales: " + result.withoutCandidates());
        System.out.println("con identidad WHOSCORED existente: " + result.withWhoScoredIdentity());
        System.out.println("CSV: " + result.output().toAbsolutePath());
    }
}
