package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.EstadisticasJugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionCotizacionRequestDTO;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.EstrategiaSimulacionResolver;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.MotorSimulacionEnMemoria;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.SimulacionCotizacionServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.SimulacionLecturaMapper;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.ValorizacionJugadorService;
import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.SimulacionFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimulacionNoPersistenciaTest {
    @Test
    void elPOSTSoloLeeYCalculaEnMemoriaSinInvocarPuertosDeEscritura() {
        UUID id = UUID.randomUUID();
        JugadorService jugadores = mock(JugadorService.class);
        EstrategiaSimulacionResolver resolver = mock(EstrategiaSimulacionResolver.class);
        MotorSimulacionEnMemoria motor = mock(MotorSimulacionEnMemoria.class);
        ValorizacionJugadorService escrituraValorizacion = mock(ValorizacionJugadorService.class);
        QuotePublicationPort publicacion = mock(QuotePublicationPort.class);
        JugadorDetalleResultado detalle = new JugadorDetalleResultado(id, "Jugador", "Liga", "Equipo", "Forward", 9,
                "AR", estadisticas(), BigDecimal.valueOf(100));
        var estrategia = new com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1(
                BigDecimal.ZERO, BigDecimal.ONE);
        when(jugadores.consultarDetalle("ply-1")).thenReturn(detalle);
        when(resolver.resolverVisible()).thenReturn(new EstrategiaSimulacionResolver.Resolucion(
                estrategia, com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad.sinUmbral()));
        when(motor.simular(eq(id), eq("Forward"), eq(5), any(), any())).thenReturn(
                new ResultadoSimulacion(id, 5, estrategia.id(), estrategia.version(), "Forward", null,
                        BigDecimal.valueOf(120), BigDecimal.ONE, SimulacionFixtures.estadisticasProyectadas()));

        new SimulacionCotizacionServiceImpl(jugadores, resolver, new SimulacionLecturaMapper(), motor)
                .simular("ply-1", new SimulacionCotizacionRequestDTO(5, Map.of("GOALS", BigDecimal.ONE)));

        verify(motor).simular(eq(id), eq("Forward"), eq(5), any(), any());
        verifyNoInteractions(escrituraValorizacion, publicacion);
    }

    private static EstadisticasJugadorResultado estadisticas() {
        return new EstadisticasJugadorResultado("Forward", 900, 2, 1, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(7), 1, 0,
                new EstadisticasJugadorResultado.Generales(10, 1, 900, 1, BigDecimal.ONE,
                        BigDecimal.valueOf(7), 1, 0),
                new EstadisticasJugadorResultado.Ofensivas(2, 1, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
                new EstadisticasJugadorResultado.Defensivas(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 0),
                new EstadisticasJugadorResultado.Pases(BigDecimal.TEN, BigDecimal.valueOf(80), BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE));
    }
}
