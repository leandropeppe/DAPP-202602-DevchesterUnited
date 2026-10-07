package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadEquipoResolver;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadJugadorResolver;
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

@ExtendWith(MockitoExtension.class)
class IdentidadJugadorResolverTest {
    @Mock JugadorRepository jugadores;
    @Mock IdentidadExternaJugadorRepository identidades;
    @Mock IdentidadEquipoResolver equipos;

    @Test
    void resuelvePorEquipoNombreYEvidenciaCompatible() {
        UUID teamId = UUID.randomUUID();
        var equipo = equipo(teamId);
        var player = new JugadorEntity(UUID.randomUUID(), "Juan Perez", teamId,
                "Forward", 9, "Argentina", true);
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(java.util.Optional.empty());
        when(equipos.resolver(any(), any())).thenReturn(new IdentidadEquipoResolver.Resultado(teamId, null));
        when(jugadores.findAllByEquipoIdAndActivoTrue(teamId)).thenReturn(List.of(player));

        var result = resolver().resolver(new IdentidadEstadisticaJugador(44, " juan  perez ",
                "Forward", "Argentina", 7L, "Equipo"), CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(result.jugadorId()).isEqualTo(player.getId());
    }

    @Test
    void rechazaCeroYMultiplesSinEvidenciaSuficiente() {
        UUID teamId = UUID.randomUUID();
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(java.util.Optional.empty());
        when(equipos.resolver(any(), any())).thenReturn(new IdentidadEquipoResolver.Resultado(teamId, null));
        when(jugadores.findAllByEquipoIdAndActivoTrue(teamId)).thenReturn(List.of());
        assertThatThrownBy(() -> resolver().resolver(hint(), CompeticionEstadistica.LA_LIGA))
                .isInstanceOf(RuntimeException.class);

        var a = new JugadorEntity(UUID.randomUUID(), "Juan Perez", teamId, "Forward", 9, "Argentina", true);
        var b = new JugadorEntity(UUID.randomUUID(), "Juan Perez", teamId, "Forward", 10, "Argentina", true);
        when(jugadores.findAllByEquipoIdAndActivoTrue(teamId)).thenReturn(List.of(a, b));
        assertThatThrownBy(() -> resolver().resolver(hint(), CompeticionEstadistica.LA_LIGA))
                .isInstanceOf(RuntimeException.class);
    }

    private IdentidadJugadorResolver resolver() { return new IdentidadJugadorResolver(jugadores, identidades, equipos); }
    private static IdentidadEstadisticaJugador hint() {
        return new IdentidadEstadisticaJugador(44, "Juan Perez", null, null, 7L, "Equipo");
    }
    private static EquipoEntity equipo(UUID id) {
        Instant now = Instant.now();
        return EquipoEntity.builder().id(id).nombre("Equipo").liga("Premier League")
                .activo(true).creadoEn(now).actualizadoEn(now).build();
    }
}
