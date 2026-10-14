package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_quote_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_market_quote_player_version",
                columnNames = {"quote_id", "version", "player_id"})
})
public class VersionCotizacionMercadoEntity {
    @EmbeddedId
    private VersionCotizacionMercadoId id;

    @Column(name = "player_id", nullable = false)
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", insertable = false, updatable = false, nullable = false)
    private JugadorEntity jugador;

    @Column(name = "price_per_token", nullable = false, precision = 20, scale = 2)
    private BigDecimal precioPorToken;

    @Column(name = "published_at", nullable = false)
    private Instant publicadoEn;

    @Column(name = "valid_from", nullable = false)
    private Instant validoDesde;

    @Column(name = "valid_until")
    private Instant validoHasta;

    @Column(name = "validity_status", nullable = false, length = 20)
    private String estadoVigencia;

    @Column(name = "strategy_reference", length = 200)
    private String referenciaEstrategia;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    protected VersionCotizacionMercadoEntity() { }

    public VersionCotizacionMercadoEntity(VersionCotizacionMercadoId id, UUID jugadorId,
            BigDecimal precioPorToken, Instant publicadoEn, Instant validoDesde,
            Instant validoHasta, String estadoVigencia, String referenciaEstrategia,
            Instant creadoEn) {
        this.id = id;
        this.jugadorId = jugadorId;
        this.precioPorToken = precioPorToken;
        this.publicadoEn = publicadoEn;
        this.validoDesde = validoDesde;
        this.validoHasta = validoHasta;
        this.estadoVigencia = estadoVigencia;
        this.referenciaEstrategia = referenciaEstrategia;
        this.creadoEn = creadoEn;
    }

    public VersionCotizacionMercadoId getId() { return id; }
    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public BigDecimal getPrecioPorToken() { return precioPorToken; }
    public Instant getPublicadoEn() { return publicadoEn; }
    public Instant getValidoDesde() { return validoDesde; }
    public Instant getValidoHasta() { return validoHasta; }
    public String getEstadoVigencia() { return estadoVigencia; }
    public String getReferenciaEstrategia() { return referenciaEstrategia; }
    public Instant getCreadoEn() { return creadoEn; }
}
