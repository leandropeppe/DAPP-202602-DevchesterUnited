package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_quote_headers")
public class CabeceraCotizacionMercadoEntity {
    @Id
    @Column(name = "player_id", nullable = false)
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", insertable = false, updatable = false, nullable = false)
    private JugadorEntity jugador;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "current_version", nullable = false)
    private Long versionActual;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "validity_status", nullable = false, length = 20)
    private String estadoVigencia;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "invalidation_reason", length = 200)
    private String invalidationReason;

    protected CabeceraCotizacionMercadoEntity() { }

    public CabeceraCotizacionMercadoEntity(UUID jugadorId, UUID quoteId,
            Long versionActual, Instant actualizadoEn) {
        this.jugadorId = jugadorId;
        this.quoteId = quoteId;
        this.versionActual = versionActual;
        this.actualizadoEn = actualizadoEn;
        this.estadoVigencia = "VALID";
    }

    public CabeceraCotizacionMercadoEntity(UUID jugadorId, UUID quoteId,
            Long versionActual, Instant actualizadoEn, String estadoVigencia,
            Instant invalidatedAt, String invalidationReason) {
        this(jugadorId, quoteId, versionActual, actualizadoEn);
        this.estadoVigencia = estadoVigencia;
        this.invalidatedAt = invalidatedAt;
        this.invalidationReason = invalidationReason;
    }

    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public UUID getQuoteId() { return quoteId; }
    public Long getVersionActual() { return versionActual; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public String getEstadoVigencia() { return estadoVigencia; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public String getInvalidationReason() { return invalidationReason; }

    /** Avanza la referencia vigente sin mutar ninguna versión histórica. */
    public void avanzarA(UUID quoteId, Long version, Instant momento) {
        if (quoteId == null || version == null || version <= 0 || momento == null) {
            throw new IllegalArgumentException("La referencia vigente es inválida.");
        }
        this.quoteId = quoteId;
        this.versionActual = version;
        this.actualizadoEn = momento;
        this.estadoVigencia = "VALID";
        this.invalidatedAt = null;
        this.invalidationReason = null;
    }

    /** Invalidates only the current header; historical versions remain immutable. */
    public void invalidar(Instant momento, String motivo) {
        if (momento == null || motivo == null || motivo.isBlank()
                || motivo.trim().length() > 200) {
            throw new IllegalArgumentException("Los datos de invalidacion son invalidos.");
        }
        if (!"VALID".equals(estadoVigencia)) {
            throw new IllegalStateException("La cotizacion ya fue invalidada.");
        }
        this.estadoVigencia = "INVALIDATED";
        this.invalidatedAt = momento;
        this.invalidationReason = motivo.trim();
        this.actualizadoEn = momento;
    }
}
