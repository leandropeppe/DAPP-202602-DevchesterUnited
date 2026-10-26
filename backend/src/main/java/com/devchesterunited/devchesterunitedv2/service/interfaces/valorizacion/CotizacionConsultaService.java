package com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion;

import com.devchesterunited.devchesterunitedv2.service.dto.valorizacion.ValuacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.valorizacion.ValuacionHistoricoDTO;

import java.util.UUID;

public interface CotizacionConsultaService {
    ValuacionDTO consultarActual(UUID jugadorId, String estrategiaId, Integer estrategiaVersion);
    ValuacionHistoricoDTO consultarHistorico(UUID jugadorId, String estrategiaId,
            Integer estrategiaVersion, int page, int limit);
}
