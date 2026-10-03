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
        var snapshot = new CatalogoSincronizado(datos.equipos, datos.jugadores, datos.ids);
        assertThat(snapshot.getEquipos()).hasSize(96);
        assertThat(snapshot.getJugadores()).hasSize(96);
        assertThat(snapshot.getEquipos()).isUnmodifiable();
    }

    @Test
    void rechazaCantidadLigaDuplicadosYReferenciasExternasInvalidas() {
        var datos = datos();
        var demasiados = new ArrayList<>(datos.equipos);
        demasiados.set(0, Equipo.importado(UUID.randomUUID(), 9999, "Otro", "O", "OTR", "LaLiga", null,
                2026, null, null, AHORA));
        assertThatThrownBy(() -> new CatalogoSincronizado(demasiados, datos.jugadores, datos.ids))
                .isInstanceOf(IllegalArgumentException.class);

        var jugadores = new ArrayList<>(datos.jugadores);
        jugadores.set(0, new Jugador(UUID.randomUUID(), "Duplicado",
                datos.jugadores.get(0).getEquipoId(), "Forward", null, "AR", AHORA));
        var ids = new HashMap<>(datos.ids);
        ids.put(jugadores.get(0).getId(), ids.get(datos.jugadores.get(1).getId()));
        assertThatThrownBy(() -> new CatalogoSincronizado(datos.equipos, jugadores, ids))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Datos datos() {
        var equipos = new ArrayList<Equipo>();
        var jugadores = new ArrayList<Jugador>();
        var ids = new HashMap<UUID, Long>();
        long externo = 1;
        for (int i = 0; i < LIGAS.size(); i++) {
            for (int j = 0; j < CANTIDADES.get(i); j++) {
                UUID id = UUID.randomUUID();
                equipos.add(Equipo.importado(id, externo++, "Equipo " + externo, "E", "E", LIGAS.get(i), null,
                        2026, null, null, AHORA));
                Jugador jugador = new Jugador(UUID.randomUUID(), "Jugador", id,
                        "Forward", null, "Argentina", AHORA);
                jugadores.add(jugador);
                ids.put(jugador.getId(), 10_000 + externo);
            }
        }
        return new Datos(equipos, jugadores, ids);
    }

    private record Datos(List<Equipo> equipos, List<Jugador> jugadores, Map<UUID, Long> ids) { }
}
