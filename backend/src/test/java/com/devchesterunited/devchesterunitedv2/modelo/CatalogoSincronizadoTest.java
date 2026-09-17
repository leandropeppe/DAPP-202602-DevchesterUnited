package com.devchesterunited.devchesterunitedv2.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        var snapshot = new CatalogoSincronizado(datos.equipos, datos.jugadores);
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
        assertThatThrownBy(() -> new CatalogoSincronizado(demasiados, datos.jugadores))
                .isInstanceOf(IllegalArgumentException.class);

        var jugadores = new ArrayList<>(datos.jugadores);
        jugadores.set(0, Jugador.importado(UUID.randomUUID(), datos.jugadores.get(1).getIdExterno(), "Duplicado",
                datos.jugadores.get(0).getEquipoId(), "Forward", null, "AR", AHORA));
        assertThatThrownBy(() -> new CatalogoSincronizado(datos.equipos, jugadores))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Datos datos() {
        var equipos = new ArrayList<Equipo>();
        var jugadores = new ArrayList<Jugador>();
        long externo = 1;
        for (int i = 0; i < LIGAS.size(); i++) {
            for (int j = 0; j < CANTIDADES.get(i); j++) {
                UUID id = UUID.randomUUID();
                equipos.add(Equipo.importado(id, externo++, "Equipo " + externo, "E", "E", LIGAS.get(i), null,
                        2026, null, null, AHORA));
                jugadores.add(Jugador.importado(UUID.randomUUID(), 10_000 + externo, "Jugador", id,
                        "Forward", null, "Argentina", AHORA));
            }
        }
        return new Datos(equipos, jugadores);
    }

    private record Datos(List<Equipo> equipos, List<Jugador> jugadores) { }
}
