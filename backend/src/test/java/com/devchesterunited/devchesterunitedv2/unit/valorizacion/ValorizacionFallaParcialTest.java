package com.devchesterunited.devchesterunitedv2.unit.valorizacion;


import static org.mockito.Mockito.*;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.ResultadoElegibilidadValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SnapshotEstadistico;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SnapshotEstadisticoService;
import com.devchesterunited.devchesterunitedv2.service.impl.valorizacion.ValorizacionJugadorServiceImpl;
import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.ValorizacionFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class ValorizacionFallaParcialTest {
    @Test
    void fallaDeUnaCombinacionNoImpidePersistirLasDemas() {
        var bueno = ValorizacionFixtures.jugador("DF", 2, new BigDecimal("7"));
        var malo = ValorizacionFixtures.jugador("DF", 3, new BigDecimal("8"));
        SnapshotEstadisticoService snapshots = mock(SnapshotEstadisticoService.class);
        when(snapshots.capturar()).thenReturn(new SnapshotEstadistico(Instant.now(), List.of(bueno, malo)));
        EjecucionValorizacionRepository ejecuciones = mock(EjecucionValorizacionRepository.class);
        when(ejecuciones.findById(any())).thenAnswer(invocation -> Optional.of(
                new EjecucionValorizacionEntity(invocation.getArgument(0), "MANUAL", Instant.now(), Instant.now(), "INICIADA")));
        CotizacionActualEstrategiaRepository actuales = mock(CotizacionActualEstrategiaRepository.class);
        doAnswer(invocation -> { if (invocation.<CotizacionActualEstrategiaEntity>getArgument(0).getJugadorId().equals(malo.jugadorId())) throw new IllegalStateException("fallo jugador"); return null; }).when(actuales).save(any());
        ValorizacionJugadorServiceImpl servicio = new ValorizacionJugadorServiceImpl(snapshots,
                new EstrategiasIniciales(List.of(new EstrategiaGeneralV1(BigDecimal.ZERO, BigDecimal.ONE)), "GENERAL", 1),
                new com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion(), ejecuciones,
                actuales, mock(HistoricoCotizacionEstrategiaRepository.class), mock(ResultadoElegibilidadValorizacionRepository.class),
                Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));

        var resultado = servicio.ejecutarManual(new ConfiguracionElegibilidad(1, 900));

        assertThat(resultado.resultados()).extracting("jugadorId").containsExactly(bueno.jugadorId());
        assertThat(resultado.fallas()).anyMatch(falla -> falla.contains(malo.jugadorId().toString()));
    }
}
