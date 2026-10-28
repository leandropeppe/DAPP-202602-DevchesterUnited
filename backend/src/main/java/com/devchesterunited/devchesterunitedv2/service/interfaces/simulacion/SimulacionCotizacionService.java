package com.devchesterunited.devchesterunitedv2.service.interfaces.simulacion;

import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionCotizacionRequestDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;

public interface SimulacionCotizacionService {
    SimulacionConfiguracionDTO obtenerConfiguracion(String playerId);

    SimulacionResultadoDTO simular(String playerId, SimulacionCotizacionRequestDTO solicitud);
}
