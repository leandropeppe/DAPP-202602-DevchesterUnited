package com.devchesterunited.devchesterunitedv2.service.dto.jugador;

public interface FiltroJugadoresConsulta {
    String liga();
    String equipo();
    String posicion();
    Integer pagina();
    Integer limite();
    long offset();
}
