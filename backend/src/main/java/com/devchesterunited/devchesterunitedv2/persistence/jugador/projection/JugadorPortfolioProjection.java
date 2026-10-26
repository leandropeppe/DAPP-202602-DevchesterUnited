package com.devchesterunited.devchesterunitedv2.persistence.jugador.projection;

import java.util.UUID;

/** Datos canónicos descriptivos usados para enriquecer una posición de portfolio. */
public interface JugadorPortfolioProjection {
    UUID getPlayerId();

    String getPlayerName();

    String getTeam();

    String getLeague();
}
