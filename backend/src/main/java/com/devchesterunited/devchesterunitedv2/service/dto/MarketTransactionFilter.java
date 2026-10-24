package com.devchesterunited.devchesterunitedv2.service.dto;

import java.time.Instant;
import java.util.UUID;

/** Filtros internos del historial personal de transacciones confirmadas. */
public record MarketTransactionFilter(
        UUID jugadorId,
        String tipoOperacion,
        Instant desde,
        Instant hasta,
        int pagina,
        int limite) {

    public MarketTransactionFilter {
        if (tipoOperacion != null
                && !tipoOperacion.equals("PURCHASE")
                && !tipoOperacion.equals("SALE")) {
            throw new IllegalArgumentException("El tipo de operacion no es valido.");
        }
        if (desde != null && hasta != null && !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El inicio debe ser anterior al fin.");
        }
        if (pagina < 1) {
            throw new IllegalArgumentException("La pagina debe ser positiva.");
        }
        if (limite < 1 || limite > 100) {
            throw new IllegalArgumentException("El limite debe estar entre 1 y 100.");
        }
    }
}
