package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.controller.dto.FiltroJugadoresDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.JugadoresResponseDTO;

public interface JugadorService {
    JugadoresResponseDTO consultar(FiltroJugadoresDTO filtros);
}
