package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorCatalogoProjection;
import com.devchesterunited.devchesterunitedv2.service.impl.JugadorServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JugadorDetalleLocalStatisticsContractTest {
    @Mock JugadorRepository jugadores;
    @Mock EstadisticasJugadorRepository estadisticas;

    @Test
    void leeSoloElJugadorYLaFilaLocalSinInvocarProveedor() {
        UUID id = UUID.randomUUID();
        JugadorCatalogoProjection jugador = mock(JugadorCatalogoProjection.class);
        when(jugadores.buscarActivoPorId(id)).thenReturn(Optional.of(jugador));
        when(jugador.getId()).thenReturn(id);
        when(jugador.getNombre()).thenReturn("Jugador");
        when(jugador.getLiga()).thenReturn("Liga");
        when(jugador.getEquipo()).thenReturn("Equipo");
        when(jugador.getPosicion()).thenReturn("Forward");
        when(jugador.getDorsal()).thenReturn(null);
        when(jugador.getNacionalidad()).thenReturn("Argentina");
        when(estadisticas.findByJugadorId(id)).thenReturn(Optional.empty());

        var resultado = new JugadorServiceImpl(jugadores, estadisticas).consultarDetalle("ply_" + id);

        assertThat(resultado.estadisticas()).isNull();
        verify(jugadores).buscarActivoPorId(id);
        verify(estadisticas).findByJugadorId(id);
    }
}
