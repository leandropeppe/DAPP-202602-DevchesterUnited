package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SnapshotEstadisticoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotEstadisticoServiceTest {

    @Test
    void usaLaPosicionDeJugadoresAunqueLaEstadisticaNoLaTenga() {
        UUID jugadorId = UUID.randomUUID();
        EstadisticasJugadorEntity estadistica = estadistica(jugadorId, null, BigDecimal.TEN);
        SnapshotEstadisticoService servicio = servicio(
                List.of(jugador(jugadorId, "Forward")), List.of(estadistica));

        var snapshot = servicio.capturar();

        assertThat(snapshot.jugadores()).singleElement()
                .extracting(JugadorEstadistico::posicion)
                .isEqualTo("Forward");
        assertThat(snapshot.jugadores().getFirst().metricas().generales().rating())
                .isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void conservaNullCuandoLaPosicionRealDelJugadorEsNull() {
        UUID jugadorId = UUID.randomUUID();
        SnapshotEstadisticoService servicio = servicio(
                List.of(jugador(jugadorId, null)), List.of(estadistica(jugadorId, null, null)));

        var snapshot = servicio.capturar();

        assertThat(snapshot.jugadores()).singleElement()
                .extracting(JugadorEstadistico::posicion)
                .isNull();
    }

    @Test
    void cargaLasPosicionesEnUnaSolaConsultaSinNMasUno() {
        UUID primerId = UUID.randomUUID();
        UUID segundoId = UUID.randomUUID();
        JugadorRepository jugadores = mock(JugadorRepository.class);
        EstadisticasJugadorRepository estadisticas = mock(EstadisticasJugadorRepository.class);
        when(jugadores.findAll()).thenReturn(List.of(
                jugador(primerId, "Forward"), jugador(segundoId, "Defender")));
        EstadisticasJugadorEntity primeraEstadistica = estadistica(primerId, null, null);
        EstadisticasJugadorEntity segundaEstadistica = estadistica(segundoId, null, null);
        when(estadisticas.findAll()).thenReturn(List.of(primeraEstadistica, segundaEstadistica));

        new SnapshotEstadisticoService(estadisticas, jugadores,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).capturar();

        verify(jugadores).findAll();
    }

    private static SnapshotEstadisticoService servicio(
            List<JugadorEntity> jugadores, List<EstadisticasJugadorEntity> estadisticas) {
        JugadorRepository jugadorRepository = mock(JugadorRepository.class);
        EstadisticasJugadorRepository estadisticasRepository = mock(EstadisticasJugadorRepository.class);
        when(jugadorRepository.findAll()).thenReturn(jugadores);
        when(estadisticasRepository.findAll()).thenReturn(estadisticas);
        return new SnapshotEstadisticoService(estadisticasRepository, jugadorRepository,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static JugadorEntity jugador(UUID id, String posicion) {
        return new JugadorEntity(id, "Jugador", UUID.randomUUID(), posicion,
                10, "Argentina", true);
    }

    private static EstadisticasJugadorEntity estadistica(UUID jugadorId, String posicion,
            BigDecimal valoracion) {
        EstadisticasJugadorEntity estadistica = mock(EstadisticasJugadorEntity.class);
        when(estadistica.getJugadorId()).thenReturn(jugadorId);
        when(estadistica.getPosicion()).thenReturn(posicion);
        when(estadistica.getValoracion()).thenReturn(valoracion);
        return estadistica;
    }
}
