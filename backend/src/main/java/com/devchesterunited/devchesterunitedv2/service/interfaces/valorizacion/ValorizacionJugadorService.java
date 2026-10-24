package com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ResultadoValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ValorizacionJugadorService {
    ResultadoEjecucion ejecutarManual(ConfiguracionElegibilidad configuracionElegibilidad);
    ResultadoEjecucion ejecutarSemanal(ConfiguracionElegibilidad configuracionElegibilidad);

    record ResultadoEjecucion(UUID ejecucionId, Instant snapshotAt,
            List<ResultadoValorizacion> resultados, List<String> fallas) {
        public ResultadoEjecucion {
            resultados = List.copyOf(resultados);
            fallas = List.copyOf(fallas);
        }
    }
}
