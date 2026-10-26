package com.devchesterunited.devchesterunitedv2.unit.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.*;

import static org.mockito.Mockito.*;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.ResultadoElegibilidadValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SnapshotEstadistico;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SnapshotEstadisticoService;
import com.devchesterunited.devchesterunitedv2.service.impl.valorizacion.ValorizacionJugadorServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.EstrategiaVisibleService;
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

class ValorizacionQuotePublicationTest {
    @Test
    void publicaSoloLaEstrategiaVisibleConReferenciaOpaca() {
        var jugador = ValorizacionFixtures.jugador("DF", 2, new BigDecimal("7"));
        var snapshot = new SnapshotEstadistico(Instant.parse("2026-10-18T00:00:00Z"), List.of(jugador));
        var snapshots = mock(SnapshotEstadisticoService.class);
        when(snapshots.capturar()).thenReturn(snapshot);
        var ejecuciones = mock(EjecucionValorizacionRepository.class);
        when(ejecuciones.findById(any())).thenAnswer(invocation -> Optional.of(
                new EjecucionValorizacionEntity(invocation.getArgument(0), "MANUAL",
                        snapshot.capturadoEn(), snapshot.capturadoEn(), "INICIADA")));
        var publicaciones = mock(QuotePublicationPort.class);
        var visible = mock(EstrategiaVisibleService.class);
        when(visible.obtener()).thenReturn(new EstrategiaVisibleService.SeleccionVisible("GENERAL", 1));
        var general = new EstrategiaGeneralV1(BigDecimal.ONE, new BigDecimal("99"));
        var positional = new EstrategiaPosicionalV1(BigDecimal.ONE, new BigDecimal("99"));

        var servicio = new ValorizacionJugadorServiceImpl(snapshots,
                new EstrategiasIniciales(List.of(general, positional), "GENERAL", 1),
                new MotorValorizacion(), ejecuciones, mock(CotizacionActualEstrategiaRepository.class),
                mock(HistoricoCotizacionEstrategiaRepository.class),
                mock(ResultadoElegibilidadValorizacionRepository.class), null, publicaciones, visible,
                Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));

        var outcome = servicio.ejecutarManual(ConfiguracionElegibilidad.sinUmbral());
        assertThat(outcome.fallas()).isEmpty();
        assertThat(outcome.resultados()).isNotEmpty();

        var request = org.mockito.ArgumentCaptor.forClass(QuotePublicationRequest.class);
        verify(publicaciones, times(1)).publish(request.capture());
        assertThat(request.getValue().playerId()).isEqualTo(jugador.jugadorId());
        assertThat(request.getValue().pricePerToken()).isPositive();
        assertThat(request.getValue().strategyReference()).isEqualTo("GENERAL:v1");
    }
}
