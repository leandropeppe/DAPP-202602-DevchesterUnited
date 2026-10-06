package com.devchesterunited.devchesterunitedv2.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CatalogoSincronizadoTest {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");
    private static final List<String> LIGAS = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
    private static final List<Integer> CANTIDADES = List.of(20, 20, 18, 20, 18);

    @Test
    void aceptaSnapshotCompletoConPertenenciaValida() {
        var datos = datos();
        var snapshot = new CatalogoSincronizado(datos.equipos, datos.jugadores, datos.ids, datos.idsEquipos);
        assertThat(snapshot.equipos()).hasSize(96);
        assertThat(snapshot.jugadores()).hasSize(96);
        assertThat(snapshot.equipos()).isUnmodifiable();
        assertThat(snapshot.jugadores()).isUnmodifiable();
        assertThat(snapshot.identidadesFootballData()).isUnmodifiable();
        assertThat(snapshot.identidadesFootballDataEquipos()).isUnmodifiable();
    }

    @Test
    void conservaSnapshotFrenteAMutacionesDeColeccionesOriginales() {
        var datos = datos();
        var equipos = new ArrayList<>(datos.equipos);
        var jugadores = new ArrayList<>(datos.jugadores);
        var ids = new HashMap<>(datos.ids);
        var idsEquipos = new HashMap<>(datos.idsEquipos);

        var snapshot = new CatalogoSincronizado(equipos, jugadores, ids, idsEquipos);

        int cantidadEquipos = snapshot.equipos().size();
        int cantidadJugadores = snapshot.jugadores().size();
        int cantidadIds = snapshot.identidadesFootballData().size();
        int cantidadIdsEquipos = snapshot.identidadesFootballDataEquipos().size();

        equipos.clear();
        jugadores.clear();
        ids.clear();
        idsEquipos.clear();

        assertThat(snapshot.equipos()).hasSize(cantidadEquipos);
        assertThat(snapshot.jugadores()).hasSize(cantidadJugadores);
        assertThat(snapshot.identidadesFootballData()).hasSize(cantidadIds);
        assertThat(snapshot.identidadesFootballDataEquipos()).hasSize(cantidadIdsEquipos);

        var equiposSnapshot = snapshot.equipos();
        var jugadoresSnapshot = snapshot.jugadores();
        var idsSnapshot = snapshot.identidadesFootballData();
        var idsEquiposSnapshot = snapshot.identidadesFootballDataEquipos();

        assertThatThrownBy(equiposSnapshot::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(jugadoresSnapshot::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(idsSnapshot::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(idsEquiposSnapshot::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rechazaCantidadLigaDuplicadosYReferenciasExternasInvalidas() {
        var datos = datos();
        var demasiados = new ArrayList<>(datos.equipos);
        UUID equipoInvalido = UUID.randomUUID();
        demasiados.set(0, Equipo.importado(equipoInvalido, "Otro", "LaLiga",
                new TemporadaEquipo(2026L, null, null), AHORA));
        var idsEquipos = new HashMap<>(datos.idsEquipos);
        idsEquipos.remove(datos.equipos.get(0).id());
        idsEquipos.put(equipoInvalido, 9999L);
        assertThatThrownBy(() -> new CatalogoSincronizado(demasiados, datos.jugadores, datos.ids, idsEquipos))
                .isInstanceOf(IllegalArgumentException.class);

        var jugadores = new ArrayList<>(datos.jugadores);
        jugadores.set(0, new Jugador(UUID.randomUUID(), "Duplicado",
                datos.jugadores.get(0).getEquipoId(), "Forward", null, "AR", AHORA));
        var ids = new HashMap<>(datos.ids);
        ids.put(jugadores.get(0).getId(), ids.get(datos.jugadores.get(1).getId()));
        assertThatThrownBy(() -> new CatalogoSincronizado(datos.equipos, jugadores, ids, datos.idsEquipos))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Datos datos() {
        var equipos = new ArrayList<Equipo>();
        var jugadores = new ArrayList<Jugador>();
        var ids = new HashMap<UUID, Long>();
        var idsEquipos = new HashMap<UUID, Long>();
        long externo = 1;
        for (int i = 0; i < LIGAS.size(); i++) {
            for (int j = 0; j < CANTIDADES.get(i); j++) {
                UUID id = UUID.randomUUID();
                long idExternoEquipo = externo++;
                equipos.add(Equipo.importado(id, "Equipo " + externo, LIGAS.get(i),
                        new TemporadaEquipo(2026L, null, null), AHORA));
                idsEquipos.put(id, idExternoEquipo);
                Jugador jugador = new Jugador(UUID.randomUUID(), "Jugador", id,
                        "Forward", null, "Argentina", AHORA);
                jugadores.add(jugador);
                ids.put(jugador.getId(), 10_000 + externo);
            }
        }
        return new Datos(equipos, jugadores, ids, idsEquipos);
    }

    private record Datos(List<Equipo> equipos, List<Jugador> jugadores,
            Map<UUID, Long> ids, Map<UUID, Long> idsEquipos) { }
}
