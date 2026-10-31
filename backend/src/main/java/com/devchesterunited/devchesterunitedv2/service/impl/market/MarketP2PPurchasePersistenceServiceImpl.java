package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AsientoP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TradeP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AsientoP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TradeP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseSettlement;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ejecuta el settlement P2P sin escribir en las tablas legacy del mercado primario.
 * El dominio calcula y persiste el PnL del vendedor junto con los cuatro efectos
 * contables P2P, sin escribir en las tablas legacy del mercado primario.
 */
@Service
public class MarketP2PPurchasePersistenceServiceImpl {
    private final CuentaCreditosMercadoRepository cuentas;
    private final TenenciaMercadoRepository tenencias;
    private final OfertaVentaP2PRepository ofertas;
    private final TradeP2PRepository trades;
    private final AsientoP2PRepository asientos;
    private final MarketP2PAccountingPersistenceService accounting;

    public MarketP2PPurchasePersistenceServiceImpl(CuentaCreditosMercadoRepository cuentas,
            TenenciaMercadoRepository tenencias, OfertaVentaP2PRepository ofertas,
            TradeP2PRepository trades, AsientoP2PRepository asientos,
            MarketP2PAccountingPersistenceService accounting) {
        this.cuentas = cuentas;
        this.tenencias = tenencias;
        this.ofertas = ofertas;
        this.trades = trades;
        this.asientos = asientos;
        this.accounting = accounting;
    }

    @Transactional
    public MarketP2PPurchaseResult liquidar(MarketP2PPurchaseSettlement settlement) {
        OfertaVentaP2PEntity offer = ofertas.findByIdForUpdate(settlement.target().offerId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.OFFER_NOT_PURCHASABLE,
                        "La oferta no esta disponible para compra."));
        validateOffer(offer, settlement);
        BigDecimal total = total(settlement.quote().pricePerToken(), settlement.quantity());
        LockedResources resources = lockResources(offer, settlement.buyerId(),
                settlement.target().playerId());

        if (resources.buyerAccount().getBalance().compareTo(total) < 0) {
            throw new TokenMarketException(TokenMarketException.Reason.INSUFFICIENT_BALANCE,
                    "El saldo de creditos es insuficiente.");
        }
        resources.buyerAccount().debitar(total, settlement.confirmedAt());
        resources.sellerAccount().acreditar(total, settlement.confirmedAt());
        BigDecimal pnl = resources.sellerHolding().consumirReserva(settlement.quantity(), total,
                settlement.confirmedAt());
        resources.buyerHolding().aplicarCompra(settlement.quantity(), total,
                settlement.confirmedAt());

        long remaining = offer.getCantidadRestante() - settlement.quantity();
        long sold = offer.getCantidadVendida() + settlement.quantity();
        String status = remaining == 0 ? OfertaVentaP2PEntity.FILLED
                : OfertaVentaP2PEntity.PARTIALLY_FILLED;
        offer.actualizarCantidades(remaining, sold, offer.getCantidadLiberada(), status,
                settlement.confirmedAt());
        assertReservation(offer, resources.sellerHolding());

        UUID tradeId = UUID.randomUUID();
        TradeP2PEntity trade = new TradeP2PEntity(
                new TradeP2PEntity.Identidad(tradeId, offer.getId(), offer.getVendedorId(),
                        settlement.buyerId(), settlement.target().playerId()),
                new TradeP2PEntity.Liquidacion(settlement.target().playerName(),
                        settlement.quantity(), settlement.quote().pricePerToken(), total),
                new TradeP2PEntity.Confirmacion(settlement.quote().quoteId(),
                        settlement.quote().version(), settlement.requestId(),
                        settlement.confirmedAt()),
                new TradeP2PEntity.Snapshot(offer.getEstado(), remaining,
                        resources.sellerHolding().getCantidadReservada()));
        trade.registrarPnlRealizado(pnl);
        trades.saveAndFlush(trade);
        accounting.persistir(trade, resources.buyerAccount().getId(),
                resources.sellerAccount().getId());
        asientos.saveAll(List.of(
                entry(tradeId, settlement.buyerId(), AsientoP2PEntity.P2P_PURCHASE,
                        settlement.quantity(), total, settlement.confirmedAt()),
                entry(tradeId, offer.getVendedorId(), AsientoP2PEntity.P2P_SALE,
                        settlement.quantity(), total, settlement.confirmedAt())));
        return result(settlement, tradeId, offer, remaining,
                resources.sellerHolding().getCantidadReservada(), total);
    }

    public MarketP2PPurchaseResult replay(UUID tradeId, UUID buyerId) {
        TradeP2PEntity trade = trades.findById(tradeId).orElseThrow(
                () -> new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                        "El resultado confirmado no esta disponible."));
        if (!buyerId.equals(trade.getCompradorId())) {
            throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                    "El resultado confirmado no esta disponible.");
        }
        if (trade.getEstadoOfertaPosterior() == null
                || trade.getCantidadRestantePosterior() == null
                || trade.getCantidadReservadaPosterior() == null) {
            throw new TokenMarketException(
                    TokenMarketException.Reason.HISTORICAL_RESULT_UNAVAILABLE,
                    "El resultado historico no conserva el snapshot de la oferta.");
        }
        return resultFromTrade(trade);
    }

    private LockedResources lockResources(OfertaVentaP2PEntity offer, UUID buyerId,
            UUID playerId) {
        UUID sellerId = offer.getVendedorId();
        CuentaCreditosMercadoEntity sellerAccount;
        CuentaCreditosMercadoEntity buyerAccount;
        TenenciaMercadoEntity sellerHolding;
        TenenciaMercadoEntity buyerHolding;
        if (sellerId.compareTo(buyerId) < 0) {
            sellerAccount = lockAccount(sellerId);
            buyerAccount = lockAccount(buyerId);
        } else {
            buyerAccount = lockAccount(buyerId);
            sellerAccount = lockAccount(sellerId);
        }
        tenencias.ensureCanonicalZero(buyerId, playerId);
        if (sellerId.compareTo(buyerId) < 0) {
            sellerHolding = lockHolding(sellerId, playerId);
            buyerHolding = lockHolding(buyerId, playerId);
        } else {
            buyerHolding = lockHolding(buyerId, playerId);
            sellerHolding = lockHolding(sellerId, playerId);
        }
        return new LockedResources(buyerAccount, sellerAccount, buyerHolding, sellerHolding);
    }

    private CuentaCreditosMercadoEntity lockAccount(UUID userId) {
        return cuentas.findByUsuarioIdForUpdate(userId).orElseThrow(
                () -> new TokenMarketException(TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La cuenta de creditos no existe."));
    }

    private TenenciaMercadoEntity lockHolding(UUID userId, UUID playerId) {
        return tenencias.findByUsuarioIdAndJugadorIdForUpdate(userId, playerId).orElseThrow(
                () -> new TokenMarketException(TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La tenencia canonica no existe."));
    }

    private void validateOffer(OfertaVentaP2PEntity offer,
            MarketP2PPurchaseSettlement settlement) {
        if ((!OfertaVentaP2PEntity.OPEN.equals(offer.getEstado())
                && !OfertaVentaP2PEntity.PARTIALLY_FILLED.equals(offer.getEstado()))
                || !offer.getJugadorId().equals(settlement.target().playerId())
                || settlement.quantity() > offer.getCantidadRestante()) {
            throw new TokenMarketException(
                    TokenMarketException.Reason.INSUFFICIENT_AVAILABLE_QUANTITY,
                    "La oferta no tiene cantidad suficiente.");
        }
    }

    private void assertReservation(OfertaVentaP2PEntity offer, TenenciaMercadoEntity holding) {
        Long active = ofertas.sumRemainingActiveByVendedorIdAndJugadorId(
                offer.getVendedorId(), offer.getJugadorId());
        if (!holding.getCantidadReservada().equals(active)) {
            throw new IllegalStateException("La reserva no coincide con las ofertas activas.");
        }
    }

    private AsientoP2PEntity entry(UUID tradeId, UUID userId, String role, Long quantity,
            BigDecimal amount, Instant createdAt) {
        return new AsientoP2PEntity(
                new AsientoP2PEntity.Identidad(UUID.randomUUID(), tradeId, userId, role),
                new AsientoP2PEntity.Importe(quantity, amount, createdAt));
    }

    private MarketP2PPurchaseResult result(MarketP2PPurchaseSettlement settlement,
            UUID tradeId, OfertaVentaP2PEntity offer, Long remaining, Long reserved,
            BigDecimal total) {
        return new MarketP2PPurchaseResult(settlement.requestId(), tradeId, offer.getId(),
                settlement.buyerId(), new MarketP2PPurchaseResult.Settlement(
                offer.getVendedorId(), offer.getJugadorId(), settlement.quantity(),
                new MarketP2PPurchaseResult.Quote(settlement.quote().quoteId(),
                        settlement.quote().version(), settlement.quote().pricePerToken()),
                new MarketP2PPurchaseResult.Quantities(settlement.quantity(), remaining,
                        reserved, offer.getEstado()), total),
                MarketIdempotencyStatus.CONFIRMED);
    }

    private MarketP2PPurchaseResult resultFromTrade(TradeP2PEntity trade) {
        return new MarketP2PPurchaseResult(trade.getSolicitudIdempotenciaId(), trade.getId(),
                trade.getOfertaId(), trade.getCompradorId(), new MarketP2PPurchaseResult.Settlement(
                trade.getVendedorId(), trade.getJugadorId(), trade.getQuantity(),
                new MarketP2PPurchaseResult.Quote(trade.getQuoteId(),
                        trade.getVersionCotizacion(), trade.getPrecioUnitario()),
                new MarketP2PPurchaseResult.Quantities(trade.getQuantity(),
                        trade.getCantidadRestantePosterior(),
                        trade.getCantidadReservadaPosterior(),
                        trade.getEstadoOfertaPosterior()), trade.getMontoTotal()),
                MarketIdempotencyStatus.CONFIRMED);
    }

    private BigDecimal total(BigDecimal unitPrice, Long quantity) {
        try {
            return unitPrice.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El importe de la compra no es representable.");
        }
    }

    private record LockedResources(CuentaCreditosMercadoEntity buyerAccount,
            CuentaCreditosMercadoEntity sellerAccount, TenenciaMercadoEntity buyerHolding,
            TenenciaMercadoEntity sellerHolding) { }
}
