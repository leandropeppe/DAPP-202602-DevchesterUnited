package com.devchesterunited.devchesterunitedv2.unit.jugador;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;

import com.devchesterunited.devchesterunitedv2.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.jugador.IdentidadJugadorResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentidadJugadorResolverStatisticsRegressionTest {
    @Mock JugadorRepository jugadores;
    @Mock IdentidadExternaJugadorRepository identidades;

    @Test
    void identidadAmbiguaSeOmiteSinCrearJugador() {
        var equipo = EquipoEntity.builder().id(UUID.randomUUID()).nombre("Equipo")
                .liga("Premier League").activo(true).creadoEn(Instant.now()).actualizadoEn(Instant.now()).build();
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId())).thenReturn(List.of(
                new JugadorEntity(UUID.randomUUID(), "Alex Smith", equipo.getId(), "Forward", 9, "Argentina", true),
                new JugadorEntity(UUID.randomUUID(), "Alex Smith", equipo.getId(), "Forward", 10, "Argentina", true)));
        var resolver = new IdentidadJugadorResolver(jugadores, identidades);
        var identidad = new IdentidadEstadisticaJugador(99L, "Alex Smith", "Forward", "Argentina", 7L, "Equipo");

        assertThatThrownBy(() -> resolver.resolver(identidad, equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
    }
}
