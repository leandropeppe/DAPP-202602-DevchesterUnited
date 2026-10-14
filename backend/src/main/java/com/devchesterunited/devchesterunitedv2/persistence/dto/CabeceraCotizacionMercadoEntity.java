package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    protected CabeceraCotizacionMercadoEntity() { }

    public CabeceraCotizacionMercadoEntity(UUID jugadorId, UUID quoteId,
            Long versionActual, Instant actualizadoEn) {
        this.jugadorId = jugadorId;
        this.quoteId = quoteId;
        this.versionActual = versionActual;
        this.actualizadoEn = actualizadoEn;
    }

    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public UUID getQuoteId() { return quoteId; }
    public Long getVersionActual() { return versionActual; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
