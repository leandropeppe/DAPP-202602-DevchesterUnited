package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "market_p2p_movements", uniqueConstraints = @UniqueConstraint(
        name = "uq_p2p_movement_trade_role", columnNames = {"trade_id", "movement_role"}))
public class MarketP2PMovementEntity {
    public record Identity(UUID id, UUID tradeId, String role, Instant createdAt) { }

    public record Asset(String assetType, UUID creditAccountId, UUID tokenUserId,
            UUID tokenCounterpartyUserId, UUID playerId, BigDecimal quantityDelta) { }

    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;
    @Column(name = "movement_role", nullable = false, length = 30)
    private String role;
    @Column(name = "asset_type", nullable = false, length = 10)
    private String assetType;
    @Column(name = "credit_account_id")
    private UUID creditAccountId;
    @Column(name = "token_user_id")
    private UUID tokenUserId;
    @Column(name = "token_counterparty_user_id")
    private UUID tokenCounterpartyUserId;
    @Column(name = "player_id")
    private UUID playerId;
    @Column(name = "quantity_delta", nullable = false, precision = 28, scale = 8)
    private BigDecimal quantityDelta;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id", insertable = false, updatable = false, nullable = false)
    private TradeP2PEntity trade;

    protected MarketP2PMovementEntity() { }

    public MarketP2PMovementEntity(Identity identity, Asset asset) {
        this.id = identity.id();
        this.tradeId = identity.tradeId();
        this.role = identity.role();
        this.createdAt = identity.createdAt();
        this.assetType = asset.assetType();
        this.creditAccountId = asset.creditAccountId();
        this.tokenUserId = asset.tokenUserId();
        this.tokenCounterpartyUserId = asset.tokenCounterpartyUserId();
        this.playerId = asset.playerId();
        this.quantityDelta = asset.quantityDelta();
    }

    public UUID getId() { return id; }
    public UUID getTradeId() { return tradeId; }
    public String getRole() { return role; }
    public String getAssetType() { return assetType; }
    public UUID getCreditAccountId() { return creditAccountId; }
    public UUID getTokenUserId() { return tokenUserId; }
    public UUID getTokenCounterpartyUserId() { return tokenCounterpartyUserId; }
    public UUID getPlayerId() { return playerId; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public Instant getCreatedAt() { return createdAt; }
    public TradeP2PEntity getTrade() { return trade; }
}
