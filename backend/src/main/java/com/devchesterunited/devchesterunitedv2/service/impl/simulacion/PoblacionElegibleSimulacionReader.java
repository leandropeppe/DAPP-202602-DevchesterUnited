package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SnapshotEstadistico;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SnapshotEstadisticoService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class PoblacionElegibleSimulacionReader {
    private final Supplier<List<JugadorEstadistico>> poblacionOficial;
    private final MotorValorizacion motor;

    @Autowired
    public PoblacionElegibleSimulacionReader(SnapshotEstadisticoService snapshots,
            MotorValorizacion motor) {
        this(() -> snapshots.capturar().jugadores(), motor);
    }

    public PoblacionElegibleSimulacionReader(Supplier<List<JugadorEstadistico>> poblacionOficial,
            MotorValorizacion motor) {
        this.poblacionOficial = Objects.requireNonNull(poblacionOficial, "La población oficial es obligatoria.");
        this.motor = Objects.requireNonNull(motor, "El motor es obligatorio.");
    }

    public List<JugadorEstadistico> leer(EstrategiaValorizacion estrategia,
            ConfiguracionElegibilidad configuracion, JugadorEstadistico escenario) {
        Objects.requireNonNull(estrategia, "La estrategia es obligatoria.");
        Objects.requireNonNull(configuracion, "La configuración es obligatoria.");
        Objects.requireNonNull(escenario, "El jugador simulado es obligatorio.");

        List<JugadorEstadistico> resultado = new ArrayList<>();
        for (JugadorEstadistico jugador : List.copyOf(poblacionOficial.get())) {
            if (jugador.jugadorId().equals(escenario.jugadorId())) continue;
            if (motor.evaluarElegibilidad(estrategia, jugador, configuracion).elegible()) {
                resultado.add(jugador);
            }
        }
        if (motor.evaluarElegibilidad(estrategia, escenario, configuracion).elegible()) {
            resultado.add(escenario);
        }
        return List.copyOf(resultado);
    }
}
