package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.EstadoEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.TemporadaEquipo;

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
        EstadoEquipo estadoValido = EstadoEquipo.nuevo(AHORA);
        assertThatThrownBy(() -> new Equipo(null, "Club", "LaLiga",
                estadoValido, TEMPORADA)).isInstanceOf(NullPointerException.class);

        UUID idNombreVacio = UUID.randomUUID();
        assertThatThrownBy(() -> Equipo.importado(idNombreVacio, "", "LaLiga", TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        UUID idNombreLargo = UUID.randomUUID();
        String nombreLargo = "a".repeat(101);
        assertThatThrownBy(() -> Equipo.importado(idNombreLargo, nombreLargo, "LaLiga", TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        UUID idLigaVacia = UUID.randomUUID();
        assertThatThrownBy(() -> Equipo.importado(idLigaVacia, "Club", "", TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        UUID idLigaLarga = UUID.randomUUID();
        String ligaLarga = "a".repeat(101);
        assertThatThrownBy(() -> Equipo.importado(idLigaLarga, "Club", ligaLarga, TEMPORADA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validaTemporada() {
        assertThatThrownBy(() -> new TemporadaEquipo(0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        LocalDate inicioTemporada = LocalDate.of(2026, 8, 1);
        assertThatThrownBy(() -> new TemporadaEquipo(2026L, inicioTemporada, null))
                .isInstanceOf(IllegalArgumentException.class);

        LocalDate inicioPosterior = LocalDate.of(2027, 1, 1);
        LocalDate finAnterior = LocalDate.of(2026, 1, 1);
        assertThatThrownBy(() -> new TemporadaEquipo(2026L, inicioPosterior, finAnterior))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TemporadaEquipo(null, null, null)).isNotNull();
    }

    @Test
    void validaEstadoYFechasDeAuditoria() {
        assertThatThrownBy(() -> new EstadoEquipo(true, null, AHORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EstadoEquipo(true, AHORA, null))
                .isInstanceOf(NullPointerException.class);

        Instant actualizadoAnterior = AHORA.minusSeconds(1);
        assertThatThrownBy(() -> new EstadoEquipo(true, AHORA, actualizadoAnterior))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
