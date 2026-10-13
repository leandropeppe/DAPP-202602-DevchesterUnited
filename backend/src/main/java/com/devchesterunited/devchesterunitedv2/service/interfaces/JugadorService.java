package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.FiltroJugadoresConsulta;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadoresResultado;

public interface JugadorService {
    JugadoresResultado consultar(FiltroJugadoresConsulta filtros);
    JugadorDetalleResultado consultarDetalle(String idPublico);
}
