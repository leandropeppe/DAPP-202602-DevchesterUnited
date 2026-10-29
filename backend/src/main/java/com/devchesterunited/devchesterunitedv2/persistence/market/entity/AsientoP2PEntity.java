package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "market_p2p_trade_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_market_p2p_entry_trade_role",
                columnNames = {"trade_id", "entry_role"}),
        @UniqueConstraint(name = "uq_market_p2p_entry_trade_user_role",
                columnNames = {"trade_id", "user_id", "entry_role"})
})
public class AsientoP2PEntity {
    public record Identidad(UUID id, UUID tradeId, UUID usuarioId, String rol) { }

    public record Importe(Long quantity, BigDecimal amount, Instant creadoEn) { }
    public static final String P2P_PURCHASE = "P2P_PURCHASE";
    public static final String P2P_SALE = "P2P_SALE";

    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;
    @Column(name = "user_id", nullable = false)
    private UUID usuarioId;
    @Column(name = "entry_role", nullable = false, length = 20)
    private String rol;
    @Column(nullable = false)
    private Long quantity;
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id", insertable = false, updatable = false, nullable = false)
    private TradeP2PEntity trade;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private UsuarioEntity usuario;

    protected AsientoP2PEntity() { }

    public AsientoP2PEntity(Identidad identidad, Importe importe) {
        this.id = identidad.id();
        this.tradeId = identidad.tradeId();
        this.usuarioId = identidad.usuarioId();
        this.rol = identidad.rol();
        this.quantity = importe.quantity();
        this.amount = importe.amount();
        this.creadoEn = importe.creadoEn();
    }

    public UUID getId() { return id; }
    public UUID getTradeId() { return tradeId; }
    public TradeP2PEntity getTrade() { return trade; }
    public UUID getUsuarioId() { return usuarioId; }
    public UsuarioEntity getUsuario() { return usuario; }
    public String getRol() { return rol; }
    public Long getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreadoEn() { return creadoEn; }
}
