package com.devchesterunited.devchesterunitedv2.unit.valorizacion;


import static org.mockito.Mockito.*;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.ResultadoElegibilidadValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.ResultadoElegibilidadValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SnapshotEstadistico;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SnapshotEstadisticoService;
import com.devchesterunited.devchesterunitedv2.service.impl.valorizacion.ValorizacionJugadorServiceImpl;
import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.ValorizacionFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class ValorizacionDatosInsuficientesTest {
    @Test
    void poblacionVaciaCompletaSinCotizaciones() {
        var elegibilidades = mock(ResultadoElegibilidadValorizacionRepository.class);
        var servicio = servicio(new SnapshotEstadistico(Instant.now(), List.of()), elegibilidades);

        var resultado = servicio.ejecutarManual(new ConfiguracionElegibilidad(1, 900));

        assertThat(resultado.resultados()).isEmpty();
        assertThat(resultado.fallas()).isEmpty();
        verifyNoInteractions(elegibilidades);
    }

    @Test
    void jugadorNoElegibleConservaCausasYNoGeneraCotizacion() {
        var jugador = ValorizacionFixtures.metricasSinRating();
        var estadistico = new com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico(
                java.util.UUID.randomUUID(), "DF", jugador);
        var elegibilidades = mock(ResultadoElegibilidadValorizacionRepository.class);
        var servicio = servicio(new SnapshotEstadistico(Instant.now(), List.of(estadistico)), elegibilidades);

        var resultado = servicio.ejecutarManual(new ConfiguracionElegibilidad(1, 900));

        assertThat(resultado.resultados()).isEmpty();
        var capturado = ArgumentCaptor.forClass(ResultadoElegibilidadValorizacionEntity.class);
        verify(elegibilidades).save(capturado.capture());
        assertThat(capturado.getValue().isElegible()).isFalse();
        assertThat(capturado.getValue().getCausasJson()).contains("METRICA_POSITIVA_AUSENTE:RATING");
    }

    private static ValorizacionJugadorServiceImpl servicio(SnapshotEstadistico snapshot,
            ResultadoElegibilidadValorizacionRepository elegibilidades) {
        SnapshotEstadisticoService snapshots = mock(SnapshotEstadisticoService.class);
        when(snapshots.capturar()).thenReturn(snapshot);
        EjecucionValorizacionRepository ejecuciones = mock(EjecucionValorizacionRepository.class);
        when(ejecuciones.findById(any())).thenAnswer(invocation -> Optional.of(
                new EjecucionValorizacionEntity(invocation.getArgument(0), "MANUAL", Instant.now(), Instant.now(), "INICIADA")));
        return new ValorizacionJugadorServiceImpl(snapshots,
                new EstrategiasIniciales(List.of(new EstrategiaGeneralV1(BigDecimal.ZERO, BigDecimal.ONE)), "GENERAL", 1),
                new com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion(), ejecuciones,
                mock(CotizacionActualEstrategiaRepository.class), mock(HistoricoCotizacionEstrategiaRepository.class),
                elegibilidades, Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));
    }
}
