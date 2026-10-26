package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.*;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.*;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleSettlement;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSalePersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Persists the sale leg in the transaction opened by the sale orchestrator.
 * The caller owns the idempotency, market, player and quote locks; this
 * component acquires the remaining rows in the approved order.
 */
@Service
public class MarketSalePersistenceServiceImpl implements MarketSalePersistenceService {
    private static final String SALE = "SALE";
    private static final String CREDIT = "CREDIT";
    private static final String TOKEN = "TOKEN";

    private final CuentaCreditosMercadoRepository cuentas;
    private final EmisionTokensMercadoRepository emisiones;
    private final TenenciaMercadoRepository tenencias;
    private final TransaccionConfirmadaMercadoRepository transacciones;
    private final MovimientoMercadoRepository movimientos;
    private final PnLRealizadoMercadoRepository pnlRealizado;

    /** Constructor retained for the T050 unit-test seam before P/L integration. */
    public MarketSalePersistenceServiceImpl(CuentaCreditosMercadoRepository cuentas,
            EmisionTokensMercadoRepository emisiones, TenenciaMercadoRepository tenencias,
            TransaccionConfirmadaMercadoRepository transacciones,
            MovimientoMercadoRepository movimientos) {
        this(cuentas, emisiones, tenencias, transacciones, movimientos, null);
    }

    @Autowired
    public MarketSalePersistenceServiceImpl(CuentaCreditosMercadoRepository cuentas,
            EmisionTokensMercadoRepository emisiones, TenenciaMercadoRepository tenencias,
            TransaccionConfirmadaMercadoRepository transacciones,
            MovimientoMercadoRepository movimientos,
            PnLRealizadoMercadoRepository pnlRealizado) {
        this.cuentas = cuentas;
        this.emisiones = emisiones;
        this.tenencias = tenencias;
        this.transacciones = transacciones;
        this.movimientos = movimientos;
        this.pnlRealizado = pnlRealizado;
    }

    @Override
    @Transactional
    public UUID persistirVenta(MarketSaleSettlement settlement) {
        validarSettlement(settlement);

        // Lock order: user account -> SUPERUSER account -> issuance -> holding.
        CuentaCreditosMercadoEntity cuentaUsuario = cuentas
                .findByUsuarioIdForUpdate(settlement.usuarioId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La cuenta de crÃ©ditos del usuario no existe."));
        CuentaCreditosMercadoEntity cuentaSuperuser = cuentas
                .findByContraparteIdForUpdate(settlement.contraparteId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La cuenta de crÃ©ditos de la contraparte no existe."));
        if (cuentaSuperuser.getBalance().compareTo(settlement.montoTotal()) < 0) {
            throw new TokenMarketException(TokenMarketException.Reason.INSUFFICIENT_BALANCE,
                    "La reserva de crÃ©ditos del superusuario es insuficiente.");
        }

        EmisionTokensMercadoEntity emision = emisiones
                .findByJugadorIdForUpdate(settlement.jugadorId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.INSUFFICIENT_INVENTORY,
                        "El jugador no tiene una emisiÃ³n operable."));
        if (emision.getCantidadEmitida() - emision.getCantidadInventario()
                < settlement.cantidad()) {
            throw new TokenMarketException(TokenMarketException.Reason.INSUFFICIENT_INVENTORY,
                    "La emisiÃ³n no permite recuperar esa cantidad de tokens.");
        }

        TenenciaMercadoEntity tenencia = tenencias
                .findByUsuarioIdAndJugadorIdForUpdate(settlement.usuarioId(), settlement.jugadorId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.INSUFFICIENT_INVENTORY,
                        "La tenencia del usuario es insuficiente."));
        if (tenencia.getQuantity() < settlement.cantidad()) {
            throw new TokenMarketException(TokenMarketException.Reason.INSUFFICIENT_INVENTORY,
                    "La tenencia del usuario es insuficiente.");
        }

        cuentaUsuario.acreditar(settlement.montoTotal(), settlement.confirmadoEn());
        cuentaSuperuser.debitar(settlement.montoTotal(), settlement.confirmadoEn());
        emision.incrementarInventario(settlement.cantidad(), settlement.confirmadoEn());
        BigDecimal pnl = tenencia.aplicarVenta(settlement.cantidad(),
                settlement.montoTotal(), settlement.confirmadoEn());
        actualizarPnl(settlement, pnl);

        UUID transaccionId = UUID.randomUUID();
        TransaccionConfirmadaMercadoEntity transaccion = new TransaccionConfirmadaMercadoEntity(
                transaccionId, settlement.usuarioId(), settlement.jugadorId(),
                settlement.nombreJugadorSnapshot(), SALE, settlement.cantidad(),
                settlement.precioUnitario(), settlement.montoTotal(), settlement.contraparteId(),
                settlement.quoteId(), settlement.versionCotizacion(), settlement.solicitudId(),
                pnl, settlement.confirmadoEn());
        transacciones.save(transaccion);

        movimientos.saveAll(List.of(
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, CREDIT,
                        cuentaUsuario.getId(), null, null, null,
                        settlement.montoTotal(), settlement.confirmadoEn()),
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, CREDIT,
                        cuentaSuperuser.getId(), null, null, null,
                        settlement.montoTotal().negate(), settlement.confirmadoEn()),
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, TOKEN,
                        null, settlement.usuarioId(), null, settlement.jugadorId(),
                        BigDecimal.valueOf(settlement.cantidad()).negate(),
                        settlement.confirmadoEn()),
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, TOKEN,
                        null, null, settlement.contraparteId(), settlement.jugadorId(),
                        BigDecimal.valueOf(settlement.cantidad()), settlement.confirmadoEn())));
        return transaccionId;
    }

    private void actualizarPnl(MarketSaleSettlement settlement, BigDecimal pnl) {
        if (pnlRealizado == null) {
            return;
        }
        PnLRealizadoMercadoEntity aggregate = pnlRealizado
                .findByUsuarioIdForUpdate(settlement.usuarioId())
                .orElseGet(() -> new PnLRealizadoMercadoEntity(settlement.usuarioId(),
                        BigDecimal.ZERO.setScale(8), settlement.confirmadoEn()));
        aggregate.acumular(pnl, settlement.confirmadoEn());
        pnlRealizado.save(aggregate);
    }

    private void validarSettlement(MarketSaleSettlement settlement) {
        if (settlement == null || settlement.solicitudId() == null
                || settlement.usuarioId() == null || settlement.jugadorId() == null
                || settlement.nombreJugadorSnapshot() == null
                || settlement.nombreJugadorSnapshot().isBlank()
                || settlement.contraparteId() == null || settlement.quoteId() == null
                || settlement.versionCotizacion() == null || settlement.versionCotizacion() <= 0
                || settlement.cantidad() == null || settlement.cantidad() <= 0
                || settlement.precioUnitario() == null || settlement.precioUnitario().signum() <= 0
                || settlement.precioUnitario().scale() > 2 || settlement.montoTotal() == null
                || settlement.montoTotal().signum() <= 0 || settlement.montoTotal().scale() > 2
                || settlement.confirmadoEn() == null) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "Los datos de liquidaciÃ³n de la venta no son vÃ¡lidos.");
        }
        BigDecimal esperado;
        try {
            esperado = settlement.precioUnitario()
                    .multiply(BigDecimal.valueOf(settlement.cantidad()))
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El importe de la venta no es representable.");
        }
        if (esperado.compareTo(settlement.montoTotal()) != 0) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El importe total no coincide con la cotizaciÃ³n.");
        }
    }
}
