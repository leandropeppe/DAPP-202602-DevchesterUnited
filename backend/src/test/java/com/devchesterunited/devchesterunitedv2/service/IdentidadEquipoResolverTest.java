package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadEquipoResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class IdentidadEquipoResolverTest {
    @Mock EquipoRepository equipos;
    @Mock IdentidadExternaEquipoRepository identidades;

    @Test
    void resuelveUnaCoincidenciaExactaIgnorandoEspaciosYMayusculas() {
        var equipo = equipo("Arsenal FC");
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(java.util.Optional.empty());
        when(equipos.findAllByActivoTrueAndLigaIgnoreCase("Premier League")).thenReturn(List.of(equipo));

        var result = resolver().resolver(hintEquipo("  arsenal   fc "), CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(result.equipoId()).isEqualTo(equipo.getId());
        assertThat(result.identidadNueva()).isNotNull();
    }

    @Test
    void rechazaCeroMultiplesYEvidenciaInsuficiente() {
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(java.util.Optional.empty());
        when(equipos.findAllByActivoTrueAndLigaIgnoreCase("Premier League")).thenReturn(List.of());
        assertThatThrownBy(() -> resolver().resolver(hintEquipo("Arsenal"), CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);

        var a = equipo("Arsenal");
        var b = equipo(" Arsenal ");
        when(equipos.findAllByActivoTrueAndLigaIgnoreCase("Premier League")).thenReturn(List.of(a, b));
        assertThatThrownBy(() -> resolver().resolver(hintEquipo("Arsenal"), CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);

        var noHint = new IdentidadEstadisticaJugador(9, "Jugador", null, null, 10L, null);
        assertThatThrownBy(() -> resolver().resolver(noHint, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
    }

    @Test
    void usaIdentidadWhoscoredPersistidaYNoElMatchingLocal() {
        var equipo = equipo("Arsenal FC");
        when(identidades.findByProveedorAndExternalId(
                eq(com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED), eq(10L)))
                .thenReturn(java.util.Optional.of(new com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity(
                        UUID.randomUUID(), equipo,
                        com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 10L)));

        assertThat(resolver().resolver(hintEquipo("Otro nombre"), CompeticionEstadistica.PREMIER_LEAGUE)
                .equipoId()).isEqualTo(equipo.getId());
        verify(equipos, org.mockito.Mockito.never()).findAllByActivoTrueAndLigaIgnoreCase("Premier League");
    }

    private IdentidadEquipoResolver resolver() { return new IdentidadEquipoResolver(equipos, identidades); }

    private static IdentidadEstadisticaJugador hintEquipo(String nombre) {
        return new IdentidadEstadisticaJugador(9, "Jugador", "Forward", "Argentina", 10L, nombre);
    }

    private static EquipoEntity equipo(String nombre) {
        Instant now = Instant.now();
        return EquipoEntity.builder().id(UUID.randomUUID()).nombre(nombre).liga("Premier League")
                .activo(true).creadoEn(now).actualizadoEn(now).build();
    }
}
