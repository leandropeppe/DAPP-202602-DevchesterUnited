package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.MarketP2PMovementEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.PnLRealizadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TradeP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MarketP2PMovementRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.PnLRealizadoMercadoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persists the P2P accounting legs within the caller's transaction. */
@Service
public class MarketP2PAccountingPersistenceService {
    private static final String CREDIT = "CREDIT";
    private static final String TOKEN = "TOKEN";

    private final MarketP2PMovementRepository movements;
    private final PnLRealizadoMercadoRepository pnlRepository;

    public MarketP2PAccountingPersistenceService(MarketP2PMovementRepository movements,
            PnLRealizadoMercadoRepository pnlRepository) {
        this.movements = movements;
        this.pnlRepository = pnlRepository;
    }

    public void persistir(TradeP2PEntity trade, UUID buyerAccountId, UUID sellerAccountId) {
        Instant now = trade.getConfirmadoEn();
        BigDecimal amount = trade.getMontoTotal();
        BigDecimal quantity = BigDecimal.valueOf(trade.getQuantity());
        updatePnl(trade.getVendedorId(), trade.getPnlRealizado(), now);
        movements.saveAll(List.of(
                credit(trade, "BUYER_CREDIT_DEBIT", buyerAccountId, amount.negate(), now),
                credit(trade, "SELLER_CREDIT_CREDIT", sellerAccountId, amount, now),
                token(trade, "SELLER_TOKEN_DEBIT", trade.getVendedorId(),
                        trade.getCompradorId(), quantity.negate(), now),
                token(trade, "BUYER_TOKEN_CREDIT", trade.getCompradorId(),
                        trade.getVendedorId(), quantity, now)));
    }

    private void updatePnl(UUID sellerId, BigDecimal pnl, Instant now) {
        PnLRealizadoMercadoEntity aggregate = pnlRepository.findByUsuarioIdForUpdate(sellerId)
                .orElseGet(() -> new PnLRealizadoMercadoEntity(sellerId,
                        BigDecimal.ZERO.setScale(8), now));
        aggregate.acumular(pnl, now);
        pnlRepository.save(aggregate);
    }

    private MarketP2PMovementEntity credit(TradeP2PEntity trade, String role,
            UUID accountId, BigDecimal delta, Instant now) {
        return new MarketP2PMovementEntity(
                new MarketP2PMovementEntity.Identity(UUID.randomUUID(), trade.getId(), role, now),
                new MarketP2PMovementEntity.Asset(CREDIT, accountId, null, null, null, delta));
    }

    private MarketP2PMovementEntity token(TradeP2PEntity trade, String role, UUID owner,
            UUID counterparty, BigDecimal delta, Instant now) {
        return new MarketP2PMovementEntity(
                new MarketP2PMovementEntity.Identity(UUID.randomUUID(), trade.getId(), role, now),
                new MarketP2PMovementEntity.Asset(TOKEN, null, owner, counterparty,
                        trade.getJugadorId(), delta));
    }
}
