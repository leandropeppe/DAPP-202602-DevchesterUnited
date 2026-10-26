package com.devchesterunited.devchesterunitedv2.configuration;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import static org.assertj.core.api.Assertions.*;

class PostgresTestSupportTest extends PostgresTestSupport {
    @Test
    void independentTransactionsCommitAndRollbackWithConcurrentVisibility() throws Exception {
        executeFixtures(sql("CREATE TABLE soporte_fixture (id INTEGER PRIMARY KEY)"));
        try {
            var barrier = new CyclicBarrier(2);
            try (var executor = Executors.newSingleThreadExecutor()) {
                var writer = executor.submit(() -> inTransaction(connection -> {
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("INSERT INTO soporte_fixture VALUES (1)");
                    }
                    awaitBarrier(barrier, Duration.ofSeconds(10));
                    awaitBarrier(barrier, Duration.ofSeconds(10));
                    return null;
                }));
                awaitBarrier(barrier, Duration.ofSeconds(10));
                try {
                    assertThat(count()).isZero();
                } finally {
                    awaitBarrier(barrier, Duration.ofSeconds(10));
                }
                writer.get(10, TimeUnit.SECONDS);
            }
            assertThat(count()).isEqualTo(1);
            assertThatThrownBy(this::insertarYFallar)
                    .isInstanceOf(IllegalStateException.class);
            assertThat(count()).isEqualTo(1);
            assertThatThrownBy(() -> executeFixtures(sql(
                    "INSERT INTO soporte_fixture VALUES (3); INSERT INTO soporte_fixture VALUES (1);")))
                    .isInstanceOf(Exception.class);
            assertThat(count()).isEqualTo(1);
        } finally {
            executeFixtures(sql("DROP TABLE soporte_fixture"));
        }
    }

    @Test
    void barrierHasBoundedWait() {
        assertThatThrownBy(() -> awaitBarrier(new CyclicBarrier(2), Duration.ofMillis(50)))
                .isInstanceOf(TimeoutException.class);
    }

    private static long count() throws Exception {
        try (var connection = openConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM soporte_fixture")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void insertarYFallar() throws Exception {
        inTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO soporte_fixture VALUES (2)");
            }
            throw new IllegalStateException("Fallo controlado");
        });
    }

    private static ByteArrayResource sql(String sql) {
        return new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
    }
}
