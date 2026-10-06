package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.FootballDataException;
import com.devchesterunited.devchesterunitedv2.adapter.ProveedorCatalogo;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.modelo.TemporadaEquipo;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.mapper.JugadorMapper;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.impl.SincronizacionCatalogoServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SincronizacionCatalogoServiceTest {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");
    @Mock ProveedorCatalogo adapter;
    @Mock EquipoRepository equipos;
    @Mock IdentidadExternaEquipoRepository identidadesEquipos;
    @Mock JugadorRepository jugadores;
    @Mock IdentidadExternaJugadorRepository identidades;
    @Mock UsuarioRepository usuarios;
    @Mock JdbcTemplate jdbc;
    private SincronizacionCatalogoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new SincronizacionCatalogoServiceImpl(adapter, equipos, identidadesEquipos, jugadores, usuarios, identidades, jdbc,
                Clock.fixed(AHORA, java.time.ZoneOffset.UTC), new JugadorMapper());
        lenient().when(identidadesEquipos.findAllByProveedorAndExternalIdIn(any(), anyList())).thenReturn(List.of());
        lenient().when(equipos.findAll()).thenReturn(List.of());
        lenient().when(jugadores.findAll()).thenReturn(List.of());
        lenient().when(identidades.findAllByProveedorAndExternalIdIn(any(), anyList())).thenReturn(List.of());
        lenient().when(identidades.findAllByProveedor(any())).thenReturn(List.of());
        lenient().when(equipos.save(any(EquipoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jugadores.save(any(JugadorEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(identidades.findByJugador_IdAndProveedor(any(), any())).thenReturn(java.util.Optional.empty());
        lenient().when(identidades.save(any(IdentidadExternaJugadorEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void publicaSnapshotValidoConConteosDeCreacion() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(true);
        when(adapter.sincronizar()).thenReturn(snapshot());

        var resultado = servicio.sincronizar();

        assertThat(resultado.equiposProcesados()).isEqualTo(96);
        assertThat(resultado.jugadoresProcesados()).isEqualTo(96);
        assertThat(resultado.equiposCreados()).isEqualTo(96);
        assertThat(resultado.jugadoresCreados()).isEqualTo(96);
        verify(equipos).flush();
        verify(jugadores).flush();
        verify(identidades, times(96)).save(any(IdentidadExternaJugadorEntity.class));
        verify(identidadesEquipos, times(96)).save(any(IdentidadExternaEquipoEntity.class));
    }

    @Test
    void resuelveJugadorExistenteDesdeIdentidadYConservaUuidInterno() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(true);
        CatalogoSincronizado snapshot = snapshot();
        UUID uuidPersistido = UUID.fromString("70000000-0000-0000-0000-000000000001");
        Equipo equipo = snapshot.equipos().get(0);
        UUID uuidEquipoPersistido = UUID.fromString("71000000-0000-0000-0000-000000000001");
        EquipoEntity equipoPersistido = new EquipoEntity(uuidEquipoPersistido, 1L, "Equipo previo", null,
                null, equipo.liga(), null, false, 2026L, null, null, AHORA.minusSeconds(120), AHORA.minusSeconds(60));
        IdentidadExternaEquipoEntity identidadEquipo = new IdentidadExternaEquipoEntity(
                UUID.fromString("72000000-0000-0000-0000-000000000001"), equipoPersistido,
                ProveedorIdentidadExterna.FOOTBALL_DATA, 1L);
        JugadorEntity jugadorPersistido = new JugadorEntity(uuidPersistido, "Jugador previo",
                equipo.id(), "Forward", 9, "Argentina", false);
        jugadorPersistido.conservarAuditoria(AHORA.minusSeconds(60), AHORA.minusSeconds(30));
        IdentidadExternaJugadorEntity identidad = new IdentidadExternaJugadorEntity(
                UUID.fromString("80000000-0000-0000-0000-000000000001"), jugadorPersistido,
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.FOOTBALL_DATA,
                10001L);
        when(identidades.findAllByProveedorAndExternalIdIn(any(), anyList())).thenReturn(List.of(identidad));
        when(identidadesEquipos.findAllByProveedorAndExternalIdIn(any(), anyList()))
                .thenReturn(List.of(identidadEquipo));
        when(identidades.findAllByProveedor(any())).thenReturn(List.of(identidad));
        when(adapter.sincronizar()).thenReturn(snapshot);

        servicio.sincronizar();

        verify(jugadores).save(argThat(j -> j.getId().equals(uuidPersistido)));
        verify(jugadores).save(argThat(j -> j.getEquipoId().equals(uuidEquipoPersistido)));
        verify(equipos).save(argThat(e -> e.getId().equals(uuidEquipoPersistido)
                && e.getIdExterno().equals(1L)));
        verify(identidadesEquipos, never()).save(argThat(i -> i.getExternalId().equals(1L)));
        verify(identidades, never()).save(argThat(i -> i.getExternalId().equals(10001L)));
    }

    @Test
    void rechazaInconsistenciaEntreIdentidadExternaYExternalIdLegacy() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(true);
        CatalogoSincronizado snapshot = snapshot();
        Equipo equipo = snapshot.equipos().get(0);
        UUID uuidEquipoPersistido = UUID.fromString("73000000-0000-0000-0000-000000000001");
        EquipoEntity equipoPersistido = new EquipoEntity(uuidEquipoPersistido, 999L, "Equipo inconsistente",
                null, null, equipo.liga(), null, true, 2026L, null, null, AHORA, AHORA);
        IdentidadExternaEquipoEntity identidadEquipo = new IdentidadExternaEquipoEntity(
                UUID.fromString("74000000-0000-0000-0000-000000000001"), equipoPersistido,
                ProveedorIdentidadExterna.FOOTBALL_DATA, 1L);
        when(identidadesEquipos.findAllByProveedorAndExternalIdIn(any(), anyList()))
                .thenReturn(List.of(identidadEquipo));
        when(adapter.sincronizar()).thenReturn(snapshot);

        assertThatThrownBy(servicio::sincronizar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide")
                .hasMessageContaining("identidad=1")
                .hasMessageContaining("legacy=999");
        verify(equipos, never()).save(any(EquipoEntity.class));
    }

    @Test
    void lockOcupadoRechazaAntesDeConsultarAdapter() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(false);
        assertThatThrownBy(servicio::sincronizar).isInstanceOf(SincronizacionEnCursoException.class);
        verifyNoInteractions(adapter);
    }

    @Test
    void falloDelProveedorSePropagaParaRollbackTransaccional() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(true);
        when(adapter.sincronizar()).thenThrow(new FootballDataException("fallo sintetico"));
        assertThatThrownBy(servicio::sincronizar).isInstanceOf(FootballDataException.class);
        verify(equipos, never()).save(any(EquipoEntity.class));
        verify(jugadores, never()).save(any(JugadorEntity.class));
    }

    private static CatalogoSincronizado snapshot() {
        var equipos = new ArrayList<Equipo>();
        var jugadores = new ArrayList<Jugador>();
        var ids = new HashMap<UUID, Long>();
        var identidadesEquipos = new ArrayList<IdentidadExternaEquipo>();
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        long externo = 1;
        for (int i = 0; i < ligas.size(); i++) for (int j = 0; j < cantidades.get(i); j++) {
            UUID id = UUID.randomUUID();
            equipos.add(Equipo.importado(id, "Equipo " + externo, ligas.get(i),
                    new TemporadaEquipo(2026L, null, null), AHORA));
            identidadesEquipos.add(new IdentidadExternaEquipo(
                    id, ProveedorIdentidadExterna.FOOTBALL_DATA, externo));
            Jugador jugador = new Jugador(UUID.randomUUID(), "Jugador Apellido", id,
                    "Forward", null, "Argentina", AHORA);
            jugadores.add(jugador);
            ids.put(jugador.getId(), 10000 + externo);
            externo++;
        }
        return new CatalogoSincronizado(equipos, jugadores, ids, identidadesEquipos);
    }
}
