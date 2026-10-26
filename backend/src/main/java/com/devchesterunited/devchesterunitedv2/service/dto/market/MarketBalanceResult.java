package com.devchesterunited.devchesterunitedv2.service.dto.market;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;

import java.math.BigDecimal;
import java.util.Optional;

/** Resultado interno de una lectura de saldo, incluyendo el estado que la habilita. */
public record MarketBalanceResult(
        EstadoMercado estado,
        long epoch,
        boolean operativa,
        Optional<BigDecimal> saldo) {
}