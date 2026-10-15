package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadJugadorResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        assertThatThrownBy(() -> new IdentidadJugadorResolver(jugadores, identidades).resolver(
                new IdentidadEstadisticaJugador(99L, "Alex Smith", "Forward", "Argentina", 7L, "Equipo"),
                equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
    }
}
