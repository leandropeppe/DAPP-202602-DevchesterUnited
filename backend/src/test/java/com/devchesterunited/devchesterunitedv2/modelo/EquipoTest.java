package com.devchesterunited.devchesterunitedv2.modelo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EquipoTest {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");
    private static final TemporadaEquipo TEMPORADA = new TemporadaEquipo(
            2026L, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31));

    @Test
    void equipoImportadoConservaIdentidadTemporadaYEstadoActivo() {
        var equipo = Equipo.importado(UUID.randomUUID(), "Club", "LaLiga", TEMPORADA, AHORA);
        assertThat(equipo.temporada().temporadaExternaId()).isEqualTo(2026L);
        assertThat(equipo.isActivo()).isTrue();
        assertThat(equipo.estado().creadoEn()).isEqualTo(AHORA);
    }

    @Test
    void desactivarYReactivarSonTransicionesInmutables() {
        var original = Equipo.importado(UUID.randomUUID(), "Club", "LaLiga", TEMPORADA, AHORA);
        var desactivado = original.desactivar(AHORA.plusSeconds(1));
        var reactivado = desactivado.reactivar(AHORA.plusSeconds(2));
        assertThat(original.isActivo()).isTrue();
        assertThat(desactivado.isActivo()).isFalse();
        assertThat(reactivado.isActivo()).isTrue();
        assertThat(reactivado.id()).isEqualTo(original.id());
        assertThat(reactivado.estado().creadoEn()).isEqualTo(original.estado().creadoEn());
        assertThat(reactivado.estado().actualizadoEn()).isEqualTo(AHORA.plusSeconds(2));
    }

    @Test
    void rechazaIdentidadYCamposObligatoriosInvalidos() {
        assertThatThrownBy(() -> new Equipo(null, "Club", "LaLiga",
                EstadoEquipo.nuevo(AHORA), TEMPORADA)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Equipo.importado(UUID.randomUUID(), "", "LaLiga", TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Equipo.importado(UUID.randomUUID(), "a".repeat(101), "LaLiga", TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Equipo.importado(UUID.randomUUID(), "Club", "", TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Equipo.importado(UUID.randomUUID(), "Club", "a".repeat(101), TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validaTemporada() {
        assertThatThrownBy(() -> new TemporadaEquipo(0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemporadaEquipo(2026L, LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemporadaEquipo(2026L, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TemporadaEquipo(null, null, null)).isNotNull();
    }

    @Test
    void validaEstadoYFechasDeAuditoria() {
        assertThatThrownBy(() -> new EstadoEquipo(true, null, AHORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EstadoEquipo(true, AHORA, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EstadoEquipo(true, AHORA, AHORA.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
