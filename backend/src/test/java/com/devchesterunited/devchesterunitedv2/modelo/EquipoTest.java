package com.devchesterunited.devchesterunitedv2.modelo;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EquipoTest {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void equipoImportadoConservaIdentidadTemporadaYEstadoActivo() {
        var equipo = Equipo.importado(UUID.randomUUID(), 42L, "Club", "C", "CLU", "LaLiga",
                URI.create("https://img.example/club.png"), 2026L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31), AHORA);
        assertThat(equipo.getIdExterno()).isEqualTo(42L);
        assertThat(equipo.getTemporadaExternaId()).isEqualTo(2026L);
        assertThat(equipo.isActivo()).isTrue();
        assertThat(equipo.getCreadoEn()).isEqualTo(AHORA);
    }

    @Test
    void desactivarYReactivarSonTransicionesInmutables() {
        var original = Equipo.importado(UUID.randomUUID(), 42L, "Club", "C", "CLU", "LaLiga", null,
                2026L, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31), AHORA);
        var desactivado = original.desactivar(AHORA.plusSeconds(1));
        var reactivado = desactivado.reactivar(AHORA.plusSeconds(2));
        assertThat(original.isActivo()).isTrue();
        assertThat(desactivado.isActivo()).isFalse();
        assertThat(reactivado.isActivo()).isTrue();
        assertThat(reactivado.getId()).isEqualTo(original.getId());
        assertThat(reactivado.getCreadoEn()).isEqualTo(original.getCreadoEn());
    }

    @Test
    void rechazaIdentidadExternaYTemporadaNoPositivas() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 2, 1);

        assertThatThrownBy(() ->
                Equipo.importado(id1, 0, "Club", "C", "CLU", "LaLiga", null,
                        2026, inicio, fin, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                Equipo.importado(id2, 1, "Club", "C", "CLU", "LaLiga", null,
                        0, inicio, fin, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
