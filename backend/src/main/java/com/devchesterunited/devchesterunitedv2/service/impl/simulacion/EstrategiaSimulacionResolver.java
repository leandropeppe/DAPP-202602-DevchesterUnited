package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionEjecucionConfig;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstrategiaAusenteException;

import java.util.Objects;

public final class EstrategiaSimulacionResolver {
    private final EstrategiasIniciales estrategias;
    private final ConfiguracionElegibilidad configuracionElegibilidad;

    public EstrategiaSimulacionResolver(EstrategiasIniciales estrategias,
            ConfiguracionElegibilidad configuracionElegibilidad) {
        this.estrategias = Objects.requireNonNull(estrategias, "Las estrategias son obligatorias.");
        this.configuracionElegibilidad = Objects.requireNonNull(configuracionElegibilidad,
                "La configuración de elegibilidad es obligatoria.");
    }

    public EstrategiaSimulacionResolver(EstrategiasIniciales estrategias,
            ValorizacionEjecucionConfig configuracion) {
        this(estrategias, Objects.requireNonNull(configuracion,
                "La configuración de valorización es obligatoria.").configuracionElegibilidad());
    }

    public Resolucion resolverVisible() {
        return estrategias.habilitadas().stream()
                .filter(estrategia -> estrategia.id().equals(estrategias.estrategiaVisibleId())
                        && estrategia.version() == estrategias.estrategiaVisibleVersion())
                .findFirst()
                .map(estrategia -> new Resolucion(estrategia, configuracionElegibilidad))
                .orElseThrow(() -> new EstrategiaAusenteException(
                        estrategias.estrategiaVisibleId() + ":v" + estrategias.estrategiaVisibleVersion()));
    }

    public record Resolucion(EstrategiaValorizacion estrategia,
            ConfiguracionElegibilidad configuracionElegibilidad) {
        public Resolucion {
            Objects.requireNonNull(estrategia, "La estrategia es obligatoria.");
            Objects.requireNonNull(configuracionElegibilidad,
                    "La configuración de elegibilidad es obligatoria.");
        }
    }
}
