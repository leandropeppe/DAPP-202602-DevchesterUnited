package com.devchesterunited.devchesterunitedv2.service.dto;

import java.util.List;
import java.util.Objects;

/** Resultado paginado del historial personal de transacciones confirmadas. */
public record MarketTransactionResult(
        List<MarketTransactionRecord> transacciones,
        PaginacionResultado paginacion) {

    public MarketTransactionResult {
        transacciones = List.copyOf(Objects.requireNonNull(transacciones));
        Objects.requireNonNull(paginacion);
    }
}
