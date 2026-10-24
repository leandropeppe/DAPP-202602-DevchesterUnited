package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Registro interno inmutable de una transaccion confirmada. */
public record MarketTransactionRecord(
        UUID id,
        UUID usuarioId,
        UUID jugadorId,
        String nombreJugadorSnapshot,
        String tipoOperacion,
        long cantidad,
        BigDecimal precioUnitario,
        BigDecimal montoTotal,
        UUID contraparteId,
        UUID quoteId,
        long quoteVersion,
        UUID solicitudIdempotenciaId,
        BigDecimal pnlRealizado,
        Instant confirmadoEn) {

    public MarketTransactionRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(jugadorId);
        Objects.requireNonNull(nombreJugadorSnapshot);
        Objects.requireNonNull(tipoOperacion);
        if (!tipoOperacion.equals("PURCHASE") && !tipoOperacion.equals("SALE")) {
            throw new IllegalArgumentException("El tipo de operacion no es valido.");
        }
        if (cantidad <= 0 || quoteVersion <= 0) {
            throw new IllegalArgumentException("La cantidad y la version deben ser positivas.");
        }
        Objects.requireNonNull(precioUnitario);
        Objects.requireNonNull(montoTotal);
        Objects.requireNonNull(contraparteId);
        Objects.requireNonNull(quoteId);
        Objects.requireNonNull(solicitudIdempotenciaId);
        Objects.requireNonNull(confirmadoEn);
    }
}
