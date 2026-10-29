package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CodigoErrorSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.DatosInsuficientesException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EscenarioSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ExcepcionSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ProyectorEstadisticas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ConfiguradorMetricasSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.PosicionAmbiguaException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.PosicionNoSoportadaException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResolutorPosicionSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV2;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
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
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class SimulacionCotizacionServiceImpl implements SimulacionCotizacionService {
    private final JugadorService jugadorService;
    private final EstrategiaSimulacionResolver estrategiaResolver;
    private final SimulacionLecturaMapper mapper;
    private final MotorSimulacionEnMemoria motor;
    private final ResolutorPosicionSimulacion posicionResolver = new ResolutorPosicionSimulacion();
    private final ConfiguradorMetricasSimulacion metricasConfigurator;

    public SimulacionCotizacionServiceImpl(JugadorService jugadorService,
            EstrategiaSimulacionResolver estrategiaResolver, SimulacionLecturaMapper mapper,
            MotorSimulacionEnMemoria motor) {
        this.jugadorService = jugadorService;
        this.estrategiaResolver = estrategiaResolver;
        this.mapper = mapper;
        this.motor = motor;
        this.metricasConfigurator = new ConfiguradorMetricasSimulacion();
    }

    @Override
    @Transactional(readOnly = true)
    public SimulacionConfiguracionDTO obtenerConfiguracion(String playerId) {
        JugadorDetalleResultado jugador = jugadorService.consultarDetalle(playerId);
        Resolucion resolucion = estrategiaResolver.resolverVisible();
        var posicion = posicionResolver.resolver(jugador == null ? null : jugador.posicion(), resolucion.estrategia());
        var metricas = jugador == null ? null : mapper.aModelo(jugador.estadisticas());
        return mapper.aConfiguracion(playerId, posicion.originales(), posicion.aplicada(),
                resolucion.estrategia(), jugador == null ? null : jugador.valuation(), metricas,
                blockingReasons(jugador, posicion, resolucion.estrategia(), metricas));
    }

    @Override
    @Transactional(readOnly = true)
    public SimulacionResultadoDTO simular(String playerId, SimulacionCotizacionRequestDTO solicitud) {
        JugadorDetalleResultado jugador = jugadorService.consultarDetalle(playerId);
        Resolucion resolucion = estrategiaResolver.resolverVisible();
        var posicion = posicionResolver.resolver(jugador == null ? null : jugador.posicion(), resolucion.estrategia());
        var metricasActuales = jugador == null ? null : mapper.aModelo(jugador.estadisticas());
        validarSimulable(jugador, posicion, resolucion.estrategia(), metricasActuales);
        Map<MetricaValorizacion, BigDecimal> modificaciones = convertirModificaciones(
                solicitud.modifications(), posicion.aplicada(), resolucion.estrategia(), metricasActuales);
        EscenarioSimulacion escenario = new EscenarioSimulacion(jugador.id(), solicitud.horizonMatches(), modificaciones);
        EstadisticasProyectadas proyectadas = ProyectorEstadisticas.proyectar(metricasActuales, escenario);
        ResultadoSimulacion resultado = motor.simular(jugador.id(), posicion.aplicada(), solicitud.horizonMatches(),
                proyectadas, resolucion);
        return mapper.aResultado(resultado, jugador.valuation());
    }

    private void validarSimulable(JugadorDetalleResultado jugador, ResolutorPosicionSimulacion.Resolucion posicion,
            EstrategiaValorizacion estrategia, com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador metricas) {
        if (jugador == null || jugador.estadisticas() == null) {
            throw new DatosInsuficientesException("estadísticas actuales");
        }
        if (!posicion.resoluble()) {
            if ("POSICION_AMBIGUA".equals(posicion.blockingReason())) throw new PosicionAmbiguaException(jugador.posicion());
            throw new PosicionNoSoportadaException(jugador.posicion());
        }
        if (metricas.generales().apps() == null || metricas.generales().minsPlayed() == null) {
            throw new DatosInsuficientesException("apps y minsPlayed actuales");
        }
        var blocking = blockingReasons(jugador, posicion, estrategia, metricas);
        if (!blocking.isEmpty()) throw new DatosInsuficientesException(String.join(", ", blocking));
    }

    private Map<MetricaValorizacion, BigDecimal> convertirModificaciones(Map<String, BigDecimal> modificaciones,
            String posicion, EstrategiaValorizacion estrategia,
            com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador actuales) {
        try {
            return modificaciones.entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> {
                MetricaValorizacion metrica = MetricaValorizacion.valueOf(entry.getKey());
                if (!metricasConfigurator.esEditable(posicion, estrategia, actuales, metrica)) {
                    throw new ExcepcionSimulacion(CodigoErrorSimulacion.METRICA_NO_EDITABLE,
                            "La métrica " + entry.getKey() + " no es editable para la posición aplicada.");
                }
                return metrica;
            }, Map.Entry::getValue));
        } catch (IllegalArgumentException ex) {
            String metrica = ex.getMessage() == null ? "desconocida" : ex.getMessage();
            throw new ExcepcionSimulacion(CodigoErrorSimulacion.METRICA_NO_EDITABLE,
                    "La métrica indicada no es editable o no existe: " + metrica + ".");
        }
    }

    private List<String> blockingReasons(JugadorDetalleResultado jugador,
            ResolutorPosicionSimulacion.Resolucion posicion, EstrategiaValorizacion estrategia,
            com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador metricas) {
        List<String> razones = new ArrayList<>();
        if (jugador == null || metricas == null) {
            razones.add("DATOS_INSUFICIENTES");
            return razones;
        }
        if (!posicion.resoluble()) razones.add(posicion.blockingReason());
        if (metricas.generales().apps() == null || metricas.generales().minsPlayed() == null) {
            razones.add("DATOS_INSUFICIENTES_APPS_MINUTES");
        }
        if ("Goalkeeper".equals(posicion.aplicada())) {
            java.util.Set.of(MetricaValorizacion.RATING, MetricaValorizacion.PASS_SUCCESS,
                    MetricaValorizacion.LONG_PASSES, MetricaValorizacion.MAN_OF_THE_MATCH).stream()
                    .filter(metrica -> metrica.extraer(metricas) == null)
                    .map(metrica -> "METRICA_POSITIVA_AUSENTE:" + metrica.name())
                    .forEach(razones::add);
        }
        if (!(estrategia instanceof EstrategiaPosicionalV2)) {
            estrategia.pesosPositivos(posicion.aplicada()).keySet().stream()
                    .filter(metrica -> metrica.extraer(metricas) == null)
                    .map(metrica -> "METRICA_POSITIVA_AUSENTE:" + metrica.name())
                    .forEach(razones::add);
        }
        return List.copyOf(razones);
    }
}
