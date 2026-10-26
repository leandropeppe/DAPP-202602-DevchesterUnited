package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionRecord;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Vista publica de una transaccion confirmada del usuario autenticado. */
public record MarketTransactionResponseDTO(
        @JsonProperty("transaction_id") UUID id,
        @JsonProperty("confirmed_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant confirmadoEn,
        @JsonProperty("player_id") UUID jugadorId,
        @JsonProperty("player_name_snapshot") String nombreJugadorSnapshot,
        @JsonProperty("operation_type") String tipoOperacion,
        long quantity,
        @JsonProperty("unit_price") BigDecimal precioUnitario,
        @JsonProperty("total_amount") BigDecimal montoTotal,
        @JsonProperty("counterparty_id") UUID contraparteId,
        @JsonProperty("quote_id") UUID quoteId,
        @JsonProperty("quote_version") long quoteVersion,
        @JsonProperty("idempotency_request_id") UUID solicitudIdempotenciaId,
        @JsonProperty("realized_pnl") @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal pnlRealizado) {

    public MarketTransactionResponseDTO {
        Objects.requireNonNull(id);
        Objects.requireNonNull(confirmadoEn);
        Objects.requireNonNull(jugadorId);
        Objects.requireNonNull(nombreJugadorSnapshot);
        Objects.requireNonNull(tipoOperacion);
        Objects.requireNonNull(precioUnitario);
        Objects.requireNonNull(montoTotal);
        Objects.requireNonNull(contraparteId);
        Objects.requireNonNull(quoteId);
        Objects.requireNonNull(solicitudIdempotenciaId);
    }

    public static MarketTransactionResponseDTO fromDomain(MarketTransactionRecord record) {
        Objects.requireNonNull(record, "La transaccion es obligatoria.");
        return new MarketTransactionResponseDTO(
                record.id(),
                record.confirmadoEn(),
                record.jugadorId(),
                record.nombreJugadorSnapshot(),
                record.tipoOperacion(),
                record.cantidad(),
                record.precioUnitario(),
                record.montoTotal(),
                record.contraparteId(),
                record.quoteId(),
                record.quoteVersion(),
                record.solicitudIdempotenciaId(),
                record.pnlRealizado());
    }
}
