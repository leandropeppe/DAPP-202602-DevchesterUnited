package com.devchesterunited.devchesterunitedv2.importer;

import com.devchesterunited.devchesterunitedv2.service.importer.WhoscoredTeamIdentityImporter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@SpringJUnitConfig(WhoscoredTeamIdentityImporterManualConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=${DB_URL}",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.main.web-application-type=none"
})
class ImportWhoscoredTeamIdentitiesTest {
    @Autowired private WhoscoredTeamIdentityImporter importer;

    @Test
    void importApprovedCsv() throws Exception {
        Path csv = Path.of(System.getProperty("whoscored.enrollment.csv",
                "config/whoscored-team-enrollment-approved.csv"));
        System.out.println(importer.importCsv(csv));
    }
}
