package com.devchesterunited.devchesterunitedv2.service.dto;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoMercado;
import java.math.BigDecimal;
import java.util.Optional;

/** Resultado interno de una lectura de saldo, incluyendo el estado que la habilita. */
public record MarketBalanceResult(
        EstadoMercado estado,
        long epoch,
        boolean operativa,
        Optional<BigDecimal> saldo) {

    public MarketBalanceResult {
        saldo = saldo == null ? Optional.empty() : saldo;
    }
}
