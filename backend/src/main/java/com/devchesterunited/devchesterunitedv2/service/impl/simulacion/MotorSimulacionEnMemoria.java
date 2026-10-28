package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ResultadoValorizacion;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class MotorSimulacionEnMemoria {
    private final MotorValorizacion motor;
    private final PoblacionElegibleSimulacionReader poblacionReader;

    public MotorSimulacionEnMemoria(MotorValorizacion motor,
            PoblacionElegibleSimulacionReader poblacionReader) {
        this.motor = Objects.requireNonNull(motor, "El motor es obligatorio.");
        this.poblacionReader = Objects.requireNonNull(poblacionReader, "El lector de población es obligatorio.");
    }

    public ResultadoSimulacion simular(UUID jugadorId, String posicion, int horizontePartidos,
            EstadisticasProyectadas proyectadas, EstrategiaSimulacionResolver.Resolucion resolucion) {
        ResultadoValorizacion resultado = calcular(jugadorId, posicion, proyectadas, resolucion);
        return resultado == null
                ? new ResultadoSimulacion(jugadorId, horizontePartidos, resolucion.estrategia().id(),
                        resolucion.estrategia().version(), posicion, null, null, null, proyectadas)
                : new ResultadoSimulacion(resultado.jugadorId(), horizontePartidos, resultado.estrategiaId(),
                        resultado.version(), resultado.elegibilidad().posicion(), null, resultado.valor(),
                        resultado.score(), proyectadas);
    }

    public ResultadoValorizacion calcular(UUID jugadorId, String posicion,
            EstadisticasProyectadas proyectadas, EstrategiaSimulacionResolver.Resolucion resolucion) {
        JugadorEstadistico escenario = JugadorEstadisticoSimulacionAdapter.adaptar(jugadorId, posicion, proyectadas);
        List<JugadorEstadistico> poblacion = poblacionReader.leer(resolucion.estrategia(),
                resolucion.configuracionElegibilidad(), escenario);
        List<ResultadoValorizacion> resultados = motor.calcular(resolucion.estrategia(), poblacion,
                resolucion.configuracionElegibilidad());
        return resultados.stream()
                .filter(item -> item.jugadorId().equals(jugadorId))
                .findFirst()
                .orElse(null);
    }
}
