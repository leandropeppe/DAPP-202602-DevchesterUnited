package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.event.JugadorActivadoEvent;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoAusenciaProcessor;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoEquipoWriter;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoJugadorActivationPublisher;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoJugadorWriter;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoSyncCoordinator;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SincronizacionResultado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoSyncComponentsTest {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");

    @Mock CatalogoEquipoWriter equipoWriter;
    @Mock CatalogoJugadorWriter jugadorWriter;
    @Mock CatalogoSincronizado snapshot;
    @Mock ApplicationEventPublisher eventos;

    @Test
    void procesaAusenciasSinInactivarLegacyEnSnapshotsPosteriores() {
        UUID jugadorLegacyId = UUID.randomUUID();
        UUID jugadorFootballDataId = UUID.randomUUID();
        JugadorEntity jugadorLegacy = jugador(jugadorLegacyId);
        JugadorEntity jugadorFootballData = jugador(jugadorFootballDataId);
        EquipoEntity equipo = EquipoEntity.builder().id(UUID.randomUUID()).nombre("Ausente")
                .liga("Premier League").activo(true).creadoEn(AHORA).actualizadoEn(AHORA).build();
        IdentidadExternaEquipoEntity identidadEquipo = new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 99L);
        when(snapshot.identidadesExternasEquipos()).thenReturn(List.of(
                new IdentidadExternaEquipo(UUID.randomUUID(),
                        ProveedorIdentidadExterna.FOOTBALL_DATA, 1L)));
        when(jugadorWriter.obtenerJugadoresConIdentidadFootballData())
                .thenReturn(Set.of(jugadorFootballDataId));
        when(jugadorWriter.obtenerTodos()).thenReturn(List.of(jugadorLegacy, jugadorFootballData));

        new CatalogoAusenciaProcessor(equipoWriter, jugadorWriter).procesar(
                snapshot, Set.of(), AHORA, false, List.of(identidadEquipo));

        verify(equipoWriter).inactivar(equipo, AHORA);
        verify(jugadorWriter).inactivar(jugadorFootballData, AHORA);
        verify(jugadorWriter, never()).inactivar(jugadorLegacy, AHORA);
    }

    @Test
    void publicaExactamenteLosJugadoresActivadosYNoPublicaConListaVacia() {
        CatalogoJugadorActivationPublisher publisher =
                new CatalogoJugadorActivationPublisher(eventos);
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();

        publisher.publicar(List.of(primero, segundo));
        publisher.publicar(List.of());

        ArgumentCaptor<JugadorActivadoEvent> captor = ArgumentCaptor.forClass(JugadorActivadoEvent.class);
        verify(eventos, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(JugadorActivadoEvent::jugadorId)
                .containsExactly(primero, segundo);
    }

    @Test
    void coordinaPersistenciaFlushAusenciasEventosYResultadoEnOrden() {
        Equipo equipo = mock(Equipo.class);
        Jugador jugador = mock(Jugador.class);
        when(snapshot.equipos()).thenReturn(List.of(equipo));
        when(snapshot.jugadores()).thenReturn(List.of(jugador));
        when(equipoWriter.obtenerIdentidadesFootballData()).thenReturn(List.of());
        CatalogoEquipoWriter.Resultado equipos = new CatalogoEquipoWriter.Resultado(
                Map.of(UUID.randomUUID(), UUID.randomUUID()), 1);
        UUID jugadorId = UUID.randomUUID();
        CatalogoJugadorWriter.Resultado jugadores = new CatalogoJugadorWriter.Resultado(
                Set.of(jugadorId), List.of(jugadorId), 1);
        when(equipoWriter.escribir(snapshot, AHORA)).thenReturn(equipos);
        when(jugadorWriter.escribir(snapshot, equipos.equiposPersistidos(), AHORA))
                .thenReturn(jugadores);
        CatalogoAusenciaProcessor ausencia = mock(CatalogoAusenciaProcessor.class);
        CatalogoJugadorActivationPublisher publisher = mock(CatalogoJugadorActivationPublisher.class);
        CatalogoSyncCoordinator coordinator = new CatalogoSyncCoordinator(
                equipoWriter, jugadorWriter, ausencia, publisher,
                Clock.fixed(AHORA, ZoneOffset.UTC));

        SincronizacionResultado resultado = coordinator.procesar(snapshot);

        InOrder orden = inOrder(equipoWriter, jugadorWriter, ausencia, publisher);
        orden.verify(equipoWriter).obtenerIdentidadesFootballData();
        orden.verify(equipoWriter).escribir(snapshot, AHORA);
        orden.verify(jugadorWriter).escribir(snapshot, equipos.equiposPersistidos(), AHORA);
        orden.verify(equipoWriter).flush();
        orden.verify(jugadorWriter).flush();
        orden.verify(ausencia).procesar(snapshot, jugadores.jugadoresSnapshot(), AHORA,
                true, List.of());
        orden.verify(publisher).publicar(jugadores.jugadoresActivados());
        assertThat(resultado.equiposCreados()).isEqualTo(1);
        assertThat(resultado.jugadoresCreados()).isEqualTo(1);
    }

    private static JugadorEntity jugador(UUID id) {
        return new JugadorEntity(id, "Jugador", UUID.randomUUID(), "Forward", null,
                "Argentina", true);
    }
}
