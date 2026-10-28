package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CodigoErrorSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.DatosInsuficientesException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EscenarioSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ExcepcionSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ProyectorEstadisticas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.EstadisticasJugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionCotizacionRequestDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.EstrategiaSimulacionResolver.Resolucion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.simulacion.SimulacionCotizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SimulacionCotizacionServiceImpl implements SimulacionCotizacionService {
    private final JugadorService jugadorService;
    private final EstrategiaSimulacionResolver estrategiaResolver;
    private final SimulacionLecturaMapper mapper;
    private final MotorSimulacionEnMemoria motor;

    public SimulacionCotizacionServiceImpl(JugadorService jugadorService,
            EstrategiaSimulacionResolver estrategiaResolver, SimulacionLecturaMapper mapper,
            MotorSimulacionEnMemoria motor) {
        this.jugadorService = jugadorService;
        this.estrategiaResolver = estrategiaResolver;
        this.mapper = mapper;
        this.motor = motor;
    }

    @Override
    @Transactional(readOnly = true)
    public SimulacionConfiguracionDTO obtenerConfiguracion(String playerId) {
        JugadorDetalleResultado jugador = jugadorService.consultarDetalle(playerId);
        Resolucion resolucion = estrategiaResolver.resolverVisible();
        validarSimulable(jugador, resolucion.estrategia());
        return mapper.aConfiguracion(playerId, List.of(jugador.posicion()), jugador.posicion(),
                resolucion.estrategia(), jugador.valuation(), mapper.aModelo(jugador.estadisticas()), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public SimulacionResultadoDTO simular(String playerId, SimulacionCotizacionRequestDTO solicitud) {
        JugadorDetalleResultado jugador = jugadorService.consultarDetalle(playerId);
        Resolucion resolucion = estrategiaResolver.resolverVisible();
        validarSimulable(jugador, resolucion.estrategia());
        Map<MetricaValorizacion, BigDecimal> modificaciones = convertirModificaciones(solicitud.modifications());
        EscenarioSimulacion escenario = new EscenarioSimulacion(jugador.id(), solicitud.horizonMatches(), modificaciones);
        EstadisticasProyectadas proyectadas = ProyectorEstadisticas.proyectar(mapper.aModelo(jugador.estadisticas()), escenario);
        ResultadoSimulacion resultado = motor.simular(jugador.id(), jugador.posicion(), solicitud.horizonMatches(),
                proyectadas, resolucion);
        return mapper.aResultado(resultado, jugador.valuation());
    }

    private void validarSimulable(JugadorDetalleResultado jugador, EstrategiaValorizacion estrategia) {
        if (jugador == null || jugador.estadisticas() == null) {
            throw new DatosInsuficientesException("estadísticas actuales");
        }
        if (jugador.posicion() == null || !estrategia.soporta(jugador.posicion())) {
            throw new ExcepcionSimulacion(CodigoErrorSimulacion.POSICION_NO_SOPORTADA,
                    "El jugador no tiene una posición soportada por la estrategia visible.");
        }
        var metricas = mapper.aModelo(jugador.estadisticas());
        if (metricas.generales().apps() == null || metricas.generales().minsPlayed() == null) {
            throw new DatosInsuficientesException("apps y minsPlayed actuales");
        }
    }

    private Map<MetricaValorizacion, BigDecimal> convertirModificaciones(Map<String, BigDecimal> modificaciones) {
        try {
            return modificaciones.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                    entry -> MetricaValorizacion.valueOf(entry.getKey()), Map.Entry::getValue));
        } catch (IllegalArgumentException ex) {
            String metrica = ex.getMessage() == null ? "desconocida" : ex.getMessage();
            throw new ExcepcionSimulacion(CodigoErrorSimulacion.METRICA_NO_EDITABLE,
                    "La métrica indicada no es editable o no existe: " + metrica + ".");
        }
    }
}
