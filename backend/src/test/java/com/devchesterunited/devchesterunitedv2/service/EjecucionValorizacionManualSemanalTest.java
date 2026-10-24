package com.devchesterunited.devchesterunitedv2.service;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.ResultadoElegibilidadValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SnapshotEstadistico;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SnapshotEstadisticoService;
import com.devchesterunited.devchesterunitedv2.service.impl.valorizacion.ValorizacionJugadorServiceImpl;
import com.devchesterunited.devchesterunitedv2.valorizacion.ValorizacionFixtures;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EjecucionValorizacionManualSemanalTest {
    @Test
    void manualYSemanalUsanElMismoSnapshotYProducenResultadosEquivalentes() {
        SnapshotEstadistico snapshot = new SnapshotEstadistico(Instant.parse("2026-10-18T00:00:00Z"),
                List.of(ValorizacionFixtures.jugador("DF", 2, new BigDecimal("7"))));
        SnapshotEstadisticoService snapshots = mock(SnapshotEstadisticoService.class);
        when(snapshots.capturar()).thenReturn(snapshot);
        EjecucionValorizacionRepository ejecuciones = mock(EjecucionValorizacionRepository.class);
        when(ejecuciones.findById(any())).thenAnswer(invocation -> Optional.of(
                new EjecucionValorizacionEntity(invocation.getArgument(0), "MANUAL", snapshot.capturadoEn(),
                        snapshot.capturadoEn(), "INICIADA")));
        ValorizacionJugadorServiceImpl servicio = servicio(snapshots, ejecuciones, new EstrategiaGeneralV1(BigDecimal.ZERO, BigDecimal.ONE));
        ConfiguracionElegibilidad configuracion = new ConfiguracionElegibilidad(1, 900);

        var manual = servicio.ejecutarManual(configuracion);
        var semanal = servicio.ejecutarSemanal(configuracion);

        assertThat(manual.snapshotAt()).isEqualTo(semanal.snapshotAt());
        assertThat(manual.resultados()).extracting("score")
                .containsExactlyElementsOf(semanal.resultados().stream().map(item -> item.score()).toList());
        verify(snapshots, times(2)).capturar();
        verify(ejecuciones, times(4)).save(any(EjecucionValorizacionEntity.class));
    }

    private static ValorizacionJugadorServiceImpl servicio(SnapshotEstadisticoService snapshots,
            EjecucionValorizacionRepository ejecuciones, EstrategiaValorizacion estrategia) {
        return new ValorizacionJugadorServiceImpl(snapshots,
                new EstrategiasIniciales(List.of(estrategia), estrategia.id(), estrategia.version()),
                new com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion(), ejecuciones,
                mock(CotizacionActualEstrategiaRepository.class), mock(HistoricoCotizacionEstrategiaRepository.class),
                mock(ResultadoElegibilidadValorizacionRepository.class),
                Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));
    }
}
