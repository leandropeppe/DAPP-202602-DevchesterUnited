package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_realized_pnl")
public class PnLRealizadoMercadoEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private UsuarioEntity usuario;

    @Column(name = "realized_pnl", nullable = false, precision = 28, scale = 8)
    private BigDecimal pnlRealizado;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected PnLRealizadoMercadoEntity() { }

    public PnLRealizadoMercadoEntity(UUID usuarioId, BigDecimal pnlRealizado,
            Instant actualizadoEn) {
        this.usuarioId = usuarioId;
        this.pnlRealizado = pnlRealizado;
        this.actualizadoEn = actualizadoEn;
    }

    public UUID getUsuarioId() { return usuarioId; }
    public UsuarioEntity getUsuario() { return usuario; }
    public BigDecimal getPnlRealizado() { return pnlRealizado; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
