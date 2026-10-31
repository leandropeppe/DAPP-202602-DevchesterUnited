package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Registro interno inmutable de una transacción confirmada. */
public record MarketTransactionRecord(Identity identity, Snapshot snapshot, Amounts amounts,
        Settlement settlement, Audit audit) {

    public record Identity(UUID id, UUID usuarioId, UUID jugadorId) {
        public Identity {
            Objects.requireNonNull(id);
            Objects.requireNonNull(usuarioId);
            Objects.requireNonNull(jugadorId);
        }
    }

    public record Snapshot(String nombreJugadorSnapshot, String tipoOperacion) {
        public Snapshot {
            Objects.requireNonNull(nombreJugadorSnapshot);
            Objects.requireNonNull(tipoOperacion);
            if (!tipoOperacion.equals("PURCHASE") && !tipoOperacion.equals("SALE")
                    && !tipoOperacion.equals("PRIMARY_PURCHASE")
                    && !tipoOperacion.equals("P2P_PURCHASE")
                    && !tipoOperacion.equals("P2P_SALE")) {
                throw new IllegalArgumentException("El tipo de operacion no es valido.");
            }
        }
    }

    public record Amounts(long cantidad, BigDecimal precioUnitario, BigDecimal montoTotal) {
        public Amounts {
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser positiva.");
            }
            Objects.requireNonNull(precioUnitario);
            Objects.requireNonNull(montoTotal);
        }
    }

    public record Settlement(UUID contraparteId, UUID quoteId, long quoteVersion) {
        public Settlement {
            Objects.requireNonNull(contraparteId);
            Objects.requireNonNull(quoteId);
            if (quoteVersion <= 0) {
                throw new IllegalArgumentException("La version debe ser positiva.");
            }
        }
    }

    public record Audit(UUID solicitudIdempotenciaId, BigDecimal pnlRealizado,
            Instant confirmadoEn) {
        public Audit {
            Objects.requireNonNull(solicitudIdempotenciaId);
            Objects.requireNonNull(confirmadoEn);
        }
    }

    public MarketTransactionRecord {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(amounts);
        Objects.requireNonNull(settlement);
        Objects.requireNonNull(audit);
    }

    public UUID id() { return identity.id(); }
    public UUID usuarioId() { return identity.usuarioId(); }
    public UUID jugadorId() { return identity.jugadorId(); }
    public String nombreJugadorSnapshot() { return snapshot.nombreJugadorSnapshot(); }
    public String tipoOperacion() { return snapshot.tipoOperacion(); }
    public long cantidad() { return amounts.cantidad(); }
    public BigDecimal precioUnitario() { return amounts.precioUnitario(); }
    public BigDecimal montoTotal() { return amounts.montoTotal(); }
    public UUID contraparteId() { return settlement.contraparteId(); }
    public UUID quoteId() { return settlement.quoteId(); }
    public long quoteVersion() { return settlement.quoteVersion(); }
    public UUID solicitudIdempotenciaId() { return audit.solicitudIdempotenciaId(); }
    public BigDecimal pnlRealizado() { return audit.pnlRealizado(); }
    public Instant confirmadoEn() { return audit.confirmadoEn(); }
}
