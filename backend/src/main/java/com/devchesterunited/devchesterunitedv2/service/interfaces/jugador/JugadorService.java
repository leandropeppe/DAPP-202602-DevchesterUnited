package com.devchesterunited.devchesterunitedv2.service.interfaces.jugador;

import com.devchesterunited.devchesterunitedv2.service.dto.jugador.FiltroJugadoresConsulta;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorRankingResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadoresResultado;

import java.util.List;

public interface JugadorService {
    JugadoresResultado consultar(FiltroJugadoresConsulta filtros);
    JugadorDetalleResultado consultarDetalle(String idPublico);
    List<JugadorRankingResultado> consultarRanking(int limite);
}
