package com.devchesterunited.devchesterunitedv2.unit.market;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.dto.market.InicializacionMercadoResultado;
import com.devchesterunited.devchesterunitedv2.service.impl.market.InicializacionCreditosUsuariosService;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationBootstrap;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationCoordinator;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationStateService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketInitializationCoordinatorTest {
    private static final String MOTIVO_FALLO_BOOTSTRAP =
            "Fallo durante bootstrap de inicializacion del mercado";

    @Test
    void iniciaEstadoYEjecutaBootstrapSinMarcarReady() {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        InicializacionMercadoResultado iniciado = resultado(EstadoMercado.INITIALIZING, 2L);
        when(estados.comenzarOReanudar()).thenReturn(iniciado);

        var coordinador = new MarketInitializationCoordinator(estados, bootstrap);

        assertThat(coordinador.coordinar()).isSameAs(iniciado);
        verify(bootstrap).bootstrap();
        verify(estados).comenzarOReanudar();
        verify(estados, never()).marcarFailed(eq(2L), Mockito.anyString());
    }

    @Test
    void noEjecutaBootstrapSiElMercadoYaEstaReady() {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        InicializacionMercadoResultado listo = resultado(EstadoMercado.READY, 5L);
        when(estados.comenzarOReanudar()).thenReturn(listo);

        var coordinador = new MarketInitializationCoordinator(estados, bootstrap);

        assertThat(coordinador.coordinar()).isSameAs(listo);
        verify(bootstrap, never()).bootstrap();
    }

    @Test
    void registraFailedConElEpochDelIntentoYPropagaElErrorOriginal() {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        InicializacionMercadoResultado iniciado = resultado(EstadoMercado.INITIALIZING, 4L);
        IllegalStateException original = new IllegalStateException("fallo del bootstrap");
        when(estados.comenzarOReanudar()).thenReturn(iniciado);
        doThrow(original).when(bootstrap).bootstrap();

        var coordinador = new MarketInitializationCoordinator(estados, bootstrap);

        assertThatThrownBy(coordinador::coordinar)
                .isSameAs(original);
        verify(estados).marcarFailed(4L, MOTIVO_FALLO_BOOTSTRAP);
    }

    @Test
    void estadoInesperadoNoEjecutaBootstrapNiRegistraFailed() {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        when(estados.comenzarOReanudar()).thenReturn(resultado(EstadoMercado.UNINITIALIZED, 1L));

        var coordinador = new MarketInitializationCoordinator(estados, bootstrap);

        assertThatThrownBy(coordinador::coordinar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Estado inesperado");
        verify(bootstrap, never()).bootstrap();
        verify(estados, never()).marcarFailed(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void conservaElErrorOriginalSiFallaElRegistroDeFailed() {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        InicializacionMercadoResultado iniciado = resultado(EstadoMercado.INITIALIZING, 4L);
        IllegalStateException original = new IllegalStateException("fallo del bootstrap");
        IllegalStateException falloPersistencia = new IllegalStateException("fallo al persistir");
        when(estados.comenzarOReanudar()).thenReturn(iniciado);
        doThrow(original).when(bootstrap).bootstrap();
        when(estados.marcarFailed(4L, MOTIVO_FALLO_BOOTSTRAP))
                .thenThrow(falloPersistencia);

        var coordinador = new MarketInitializationCoordinator(estados, bootstrap);

        assertThatThrownBy(coordinador::coordinar)
                .isSameAs(original);
        assertThat(original.getSuppressed()).containsExactly(falloPersistencia);
    }

    @Test
    void completaCreditosBootstrapYReadySoloDespuesDeTodasLasEtapas() {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        InicializacionCreditosUsuariosService creditos =
                mock(InicializacionCreditosUsuariosService.class);
        InicializacionMercadoResultado iniciado = resultado(EstadoMercado.INITIALIZING, 8L);
        InicializacionMercadoResultado listo = resultado(EstadoMercado.READY, 9L);
        when(estados.comenzarOReanudar()).thenReturn(iniciado);
        when(estados.marcarReady(8L)).thenReturn(Optional.of(listo));

        var coordinador = new MarketInitializationCoordinator(
                estados, bootstrap, creditos, null);

        assertThat(coordinador.coordinar()).isSameAs(listo);

        InOrder orden = inOrder(creditos, bootstrap, estados);
        orden.verify(creditos).asignarAUsuariosExistentes();
        orden.verify(bootstrap).bootstrap();
        orden.verify(estados).marcarReady(8L);
    }

    @Test
    void unFalloDeEtapaDejaFailedYElSiguienteIntentoReanudaSinDuplicarLaCoordinacion() {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        InicializacionCreditosUsuariosService creditos =
                mock(InicializacionCreditosUsuariosService.class);
        InicializacionMercadoResultado primerIntento =
                resultado(EstadoMercado.INITIALIZING, 10L);
        InicializacionMercadoResultado reintento =
                resultado(EstadoMercado.INITIALIZING, 12L);
        InicializacionMercadoResultado listo = resultado(EstadoMercado.READY, 13L);
        when(estados.comenzarOReanudar()).thenReturn(primerIntento, reintento);
        when(estados.marcarFailed(10L, MOTIVO_FALLO_BOOTSTRAP))
                .thenReturn(Optional.of(resultado(EstadoMercado.FAILED, 11L)));
        when(estados.marcarReady(12L)).thenReturn(Optional.of(listo));
        doThrow(new IllegalStateException("fallo de créditos"))
                .doNothing().when(creditos).asignarAUsuariosExistentes();

        var coordinador = new MarketInitializationCoordinator(
                estados, bootstrap, creditos, null);

        assertThatThrownBy(coordinador::coordinar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo de créditos");
        assertThat(coordinador.coordinar()).isSameAs(listo);

        verify(estados).marcarFailed(10L, MOTIVO_FALLO_BOOTSTRAP);
        verify(bootstrap).bootstrap();
    }

    @Test
    void dosInvocacionesNoEjecutanBootstrapSimultaneamente() throws Exception {
        MarketInitializationStateService estados = mock(MarketInitializationStateService.class);
        MarketInitializationBootstrap bootstrap = mock(MarketInitializationBootstrap.class);
        InicializacionMercadoResultado iniciado = resultado(EstadoMercado.INITIALIZING, 2L);
        CountDownLatch bootstrapIniciado = new CountDownLatch(1);
        CountDownLatch liberarBootstrap = new CountDownLatch(1);
        CountDownLatch segundaInvocacionIniciada = new CountDownLatch(1);
        AtomicReference<Thread> segundoHilo = new AtomicReference<>();
        AtomicInteger activos = new AtomicInteger();
        AtomicInteger maximosActivos = new AtomicInteger();
        when(estados.comenzarOReanudar()).thenReturn(iniciado);
        doAnswer(invocacion -> {
            int actuales = activos.incrementAndGet();
            maximosActivos.accumulateAndGet(actuales, Math::max);
            bootstrapIniciado.countDown();
            liberarBootstrap.await(10, TimeUnit.SECONDS);
            activos.decrementAndGet();
            return null;
        }).when(bootstrap).bootstrap();

        var coordinador = new MarketInitializationCoordinator(estados, bootstrap);
        ReentrantLock lock = lockDelCoordinador(coordinador);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> primero = executor.submit(coordinador::coordinar);
            assertThat(bootstrapIniciado.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> segundo = executor.submit(() -> {
                segundoHilo.set(Thread.currentThread());
                segundaInvocacionIniciada.countDown();
                coordinador.coordinar();
            });
            assertThat(segundaInvocacionIniciada.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(esperarThreadEnCola(lock, segundoHilo.get()))
                    .as("la segunda invocacion debe estar encolada en el ReentrantLock")
                    .isTrue();
            verify(bootstrap, times(1)).bootstrap();
            liberarBootstrap.countDown();
            primero.get(10, TimeUnit.SECONDS);
            segundo.get(10, TimeUnit.SECONDS);
        } finally {
            liberarBootstrap.countDown();
            executor.shutdownNow();
        }

        assertThat(maximosActivos).hasValue(1);
        verify(bootstrap, times(2)).bootstrap();
    }

    private static boolean esperarThreadEnCola(ReentrantLock lock, Thread thread) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            if (lock.hasQueuedThread(thread)) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        return false;
    }

    private static ReentrantLock lockDelCoordinador(
            MarketInitializationCoordinator coordinador) {
        try {
            Field field = MarketInitializationCoordinator.class
                    .getDeclaredField("intentoEnCurso");
            field.setAccessible(true);
            return (ReentrantLock) field.get(coordinador);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("No se pudo inspeccionar el lock del coordinador", exception);
        }
    }

    private static InicializacionMercadoResultado resultado(EstadoMercado estado, long epoch) {
        return new InicializacionMercadoResultado(estado, epoch, null);
    }
}
