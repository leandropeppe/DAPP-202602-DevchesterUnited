package com.devchesterunited.devchesterunitedv2.persistence.jugador.projection;

import java.math.BigDecimal;
import java.util.UUID;

/** Resultado de catálogo con equipo y liga resueltos desde equipos. */
public interface JugadorCatalogoProjection {
    UUID getId();
    String getNombre();
    String getLiga();
    String getEquipo();
    String getPosicion();
    Integer getDorsal();
    String getNacionalidad();
    BigDecimal getValuation();
    String getStrategyId();
    Integer getStrategyVersion();
}
