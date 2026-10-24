package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationStateService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketInitializationStateServiceTest {
    private static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");
    private static final BigDecimal RESERVA = new BigDecimal("1000000.00");

    @Mock
    private EstadoMercadoRepository estados;

    private MarketInitializationStateService servicio;

    @BeforeEach
    void preparar() {
        servicio = new MarketInitializationStateService(
                estados, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void pasaDeUninitializedAInitializingIncrementandoEpochUnaVez() {
        EstadoMercadoEntity estado = estado("UNINITIALIZED", 1L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        var resultado = servicio.comenzarOReanudar();

        assertThat(resultado.estado()).isEqualTo(EstadoMercado.INITIALIZING);
        assertThat(resultado.epoca()).isEqualTo(2L);
        verify(estados).ensureSingleton(AHORA);
        verify(estados).save(estado);
    }

    @Test
    void reintentaDesdeFailedYContinuaElEpoch() {
        EstadoMercadoEntity estado = estado("FAILED", 3L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        var resultado = servicio.comenzarOReanudar();

        assertThat(resultado.estado()).isEqualTo(EstadoMercado.INITIALIZING);
        assertThat(resultado.epoca()).isEqualTo(4L);
    }

    @Test
    void reanudaInitializingSinCrearUnaNuevaTransicion() {
        EstadoMercadoEntity estado = estado("INITIALIZING", 2L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        var resultado = servicio.comenzarOReanudar();

        assertThat(resultado.epoca()).isEqualTo(2L);
        verify(estados, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void readyEsIdempotente() {
        EstadoMercadoEntity estado = estado("READY", 7L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        var resultado = servicio.comenzarOReanudar();

        assertThat(resultado.estado()).isEqualTo(EstadoMercado.READY);
        assertThat(resultado.epoca()).isEqualTo(7L);
        verify(estados, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void persisteFailedEnLaEjecucionConElEpochEsperado() {
        EstadoMercadoEntity estado = estado("INITIALIZING", 2L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        var resultado = servicio.marcarFailed(2L, "fallo de etapa");

        assertThat(resultado).isPresent();
        assertThat(resultado.orElseThrow().estado())
                .isEqualTo(EstadoMercado.FAILED);
        assertThat(resultado.orElseThrow().epoca()).isEqualTo(3L);
        assertThat(estado.getMotivoFallo()).isEqualTo("fallo de etapa");
        verify(estados).save(estado);
    }

    @Test
    void noPermiteQueUnEpochAntiguoMarqueFailed() {
        EstadoMercadoEntity estado = estado("INITIALIZING", 5L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        assertThat(servicio.marcarFailed(4L, "ejecucion antigua")).isEmpty();
        assertThat(estado.getStatus()).isEqualTo("INITIALIZING");
        assertThat(estado.getEpoch()).isEqualTo(5L);
        verify(estados, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void marcaReadySoloParaElEpochInitializingPropio() {
        EstadoMercadoEntity estado = estado("INITIALIZING", 6L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        var resultado = servicio.marcarReady(6L).orElseThrow();

        assertThat(resultado.estado()).isEqualTo(EstadoMercado.READY);
        assertThat(resultado.epoca()).isEqualTo(7L);
        verify(estados).save(estado);
    }

    @Test
    void noMarcaReadyConEpochAntiguo() {
        EstadoMercadoEntity estado = estado("INITIALIZING", 7L);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        assertThat(servicio.marcarReady(6L)).isEmpty();
        assertThat(estado.getStatus()).isEqualTo("INITIALIZING");
        assertThat(estado.getEpoch()).isEqualTo(7L);
        verify(estados, org.mockito.Mockito.never()).save(any());
    }

    private static EstadoMercadoEntity estado(String status, long epoch) {
        return new EstadoMercadoEntity(
                (short) 1, status, epoch, RESERVA, RESERVA, null, AHORA, AHORA);
    }
}
