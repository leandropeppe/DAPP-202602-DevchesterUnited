package com.devchesterunited.devchesterunitedv2.integration.market;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationExecutionLock;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MarketInitializationExecutionLockIntegrationTest extends PostgresTestSupport {
    @Autowired
    private MarketInitializationExecutionLock lock;

    @Test
    void serializaEjecucionesEntreHilosConElLockPostgresql() throws Exception {
        CountDownLatch primeraIniciada = new CountDownLatch(1);
        CountDownLatch liberarPrimera = new CountDownLatch(1);
        CountDownLatch segundaIntentando = new CountDownLatch(1);
        AtomicInteger activas = new AtomicInteger();
        AtomicInteger maximasActivas = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> primera = executor.submit(() -> lock.ejecutar(() -> {
                int actual = activas.incrementAndGet();
                maximasActivas.accumulateAndGet(actual, Math::max);
                primeraIniciada.countDown();
                try {
                    liberarPrimera.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    activas.decrementAndGet();
                }
                return null;
            }));
            assertThat(primeraIniciada.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> segunda = executor.submit(() -> {
                segundaIntentando.countDown();
                return lock.ejecutar(() -> {
                    int actual = activas.incrementAndGet();
                    maximasActivas.accumulateAndGet(actual, Math::max);
                    activas.decrementAndGet();
                    return null;
                });
            });

            assertThat(segundaIntentando.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(segunda.isDone()).isFalse();
            liberarPrimera.countDown();
            primera.get(10, TimeUnit.SECONDS);
            segunda.get(10, TimeUnit.SECONDS);
        } finally {
            liberarPrimera.countDown();
            executor.shutdownNow();
        }

        assertThat(maximasActivas).hasValue(1);
    }

    @Test
    void liberaElLockCuandoLaOperacionProtegidaFalla() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                lock.ejecutar(() -> {
                    throw new IllegalStateException("fallo controlado");
                })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo controlado");

        assertThat(lock.ejecutar(() -> "ejecucion posterior")).isEqualTo("ejecucion posterior");
    }
}
