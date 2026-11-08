package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.TemporadaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.mapper.JugadorMapper;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoEquipoWriter;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoJugadorWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoWritersTest {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");

    @Mock EquipoRepository equipos;
    @Mock IdentidadExternaEquipoRepository identidadesEquipos;
    @Mock JugadorRepository jugadores;
    @Mock IdentidadExternaJugadorRepository identidadesJugadores;

    @Test
    void escritorDeEquiposConservaUuidYNoDuplicaIdentidad() {
        CatalogoSincronizado snapshot = snapshot();
        Equipo equipo = snapshot.equipos().get(0);
        UUID uuidPersistido = UUID.randomUUID();
        EquipoEntity entidad = EquipoEntity.builder().id(uuidPersistido).nombre("Anterior")
                .liga(equipo.liga()).activo(false).temporadaExternaId(2025L)
                .creadoEn(AHORA.minusSeconds(60)).actualizadoEn(AHORA.minusSeconds(30)).build();
        when(identidadesEquipos.findAllByProveedorAndExternalIdIn(any(), anyList()))
                .thenReturn(List.of(new IdentidadExternaEquipoEntity(
                        UUID.randomUUID(), entidad, ProveedorIdentidadExterna.FOOTBALL_DATA, 1L)));
        when(equipos.save(any(EquipoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CatalogoEquipoWriter.Resultado resultado = new CatalogoEquipoWriter(equipos, identidadesEquipos)
                .escribir(snapshot, AHORA);

        assertThat(resultado.creados()).isEqualTo(95);
        assertThat(resultado.equiposPersistidos().get(equipo.id())).isEqualTo(uuidPersistido);
        verify(identidadesEquipos, never()).save(argThat(i -> i.getExternalId() == 1L));
    }

    @Test
    void escritorDeJugadoresAsociaEquipoYFallaSiNoPuedeResolverlo() {
        CatalogoSincronizado snapshot = snapshot();
        Map<UUID, UUID> equiposPersistidos = new HashMap<>();
        snapshot.equipos().forEach(equipo -> equiposPersistidos.put(equipo.id(), equipo.id()));
        when(identidadesJugadores.findAllByProveedorAndExternalIdIn(any(), anyList()))
                .thenReturn(List.of());
        when(jugadores.save(any(JugadorEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CatalogoJugadorWriter.Resultado resultado = new CatalogoJugadorWriter(
                jugadores, identidadesJugadores, new JugadorMapper()).escribir(
                snapshot, equiposPersistidos, AHORA);

        assertThat(resultado.creados()).isEqualTo(96);
        assertThat(resultado.jugadoresSnapshot()).hasSize(96);
        verify(identidadesJugadores, times(96)).save(any(IdentidadExternaJugadorEntity.class));

        equiposPersistidos.clear();
        assertThatThrownBy(() -> new CatalogoJugadorWriter(
                jugadores, identidadesJugadores, new JugadorMapper()).escribir(
                snapshot, equiposPersistidos, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No se pudo resolver el equipo local del jugador.");
    }

    private static CatalogoSincronizado snapshot() {
        List<Equipo> equipos = new ArrayList<>();
        List<Jugador> jugadores = new ArrayList<>();
        Map<UUID, Long> ids = new HashMap<>();
        List<IdentidadExternaEquipo> identidades = new ArrayList<>();
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        int i = 0;
        for (int liga = 0; liga < ligas.size(); liga++) {
            for (int posicion = 0; posicion < cantidades.get(liga); posicion++, i++) {
            UUID equipoId = UUID.randomUUID();
            equipos.add(Equipo.importado(equipoId, "Equipo " + i, ligas.get(liga),
                    new TemporadaEquipo(2026L, null, null), AHORA));
            identidades.add(new IdentidadExternaEquipo(equipoId,
                    ProveedorIdentidadExterna.FOOTBALL_DATA, (long) i + 1));
            Jugador jugador = new Jugador(UUID.randomUUID(), "Jugador " + i, equipoId,
                    "Forward", null, "Argentina", AHORA);
            jugadores.add(jugador);
            ids.put(jugador.getId(), 10000L + i);
            }
        }
        return new CatalogoSincronizado(equipos, jugadores, ids, identidades);
    }
}
