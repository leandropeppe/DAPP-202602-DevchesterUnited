package com.devchesterunited.devchesterunitedv2.e2e.observability;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el acceso JDBC real y el esquema migrado del contexto de integracion.
 *
 * <p>PostgresTestSupport usa un PostgreSQL de Testcontainers; por tanto esta prueba
 * no acredita la resolucion DNS {@code postgres} del Compose de observabilidad. Esa
 * topologia se valida en tiempo de ejecucion levantando el Compose especifico.</p>
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class PostgresDockerConnectivityTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextConnectsToPostgresAndAppliesFlywayMigrations() {
        Integer connectionValue = jdbc.queryForObject("SELECT 1", Integer.class);
        Integer successfulMigrations = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(connectionValue).isEqualTo(1);
        assertThat(successfulMigrations).isNotNull().isPositive();
    }
}
