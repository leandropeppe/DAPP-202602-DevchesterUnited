package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EmisionTokensMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.MovimientoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.MovimientoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseSettlement;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketPurchasePersistenceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the purchase leg in one transaction. The caller owns the earlier
 * idempotency, market, player and quote locks; this component acquires the
 * remaining rows in the SDD order and never starts an independent transaction.
 */
@Service
public class MarketPurchasePersistenceServiceImpl implements MarketPurchasePersistenceService {
    private static final String PURCHASE = "PURCHASE";
    private static final String CREDIT = "CREDIT";
    private static final String TOKEN = "TOKEN";

    private final CuentaCreditosMercadoRepository cuentas;
    private final EmisionTokensMercadoRepository emisiones;
    private final TenenciaMercadoRepository tenencias;
    private final TransaccionConfirmadaMercadoRepository transacciones;
    private final MovimientoMercadoRepository movimientos;

    public MarketPurchasePersistenceServiceImpl(CuentaCreditosMercadoRepository cuentas,
            EmisionTokensMercadoRepository emisiones, TenenciaMercadoRepository tenencias,
            TransaccionConfirmadaMercadoRepository transacciones,
            MovimientoMercadoRepository movimientos) {
        this.cuentas = cuentas;
        this.emisiones = emisiones;
        this.tenencias = tenencias;
        this.transacciones = transacciones;
        this.movimientos = movimientos;
    }

    @Override
    @Transactional
    public UUID persistirCompra(MarketPurchaseSettlement settlement) {
        validarSettlement(settlement);

        CuentaCreditosMercadoEntity cuentaUsuario = cuentas
                .findByUsuarioIdForUpdate(settlement.usuarioId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La cuenta de créditos del usuario no existe."));
        CuentaCreditosMercadoEntity cuentaSuperuser = cuentas
                .findByContraparteIdForUpdate(settlement.contraparteId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La cuenta de créditos de la contraparte no existe."));
        EmisionTokensMercadoEntity emision = emisiones
                .findByJugadorIdForUpdate(settlement.jugadorId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.INSUFFICIENT_INVENTORY,
                        "El jugador no tiene una emisión operable."));

        if (cuentaUsuario.getBalance().compareTo(settlement.montoTotal()) < 0) {
            throw new TokenMarketException(TokenMarketException.Reason.INSUFFICIENT_BALANCE,
                    "El saldo de créditos es insuficiente.");
        }
        if (emision.getCantidadInventario() < settlement.cantidad()) {
            throw new TokenMarketException(
                    TokenMarketException.Reason.INSUFFICIENT_INVENTORY,
                    "El inventario de tokens es insuficiente.");
        }

        tenencias.ensureCanonicalZero(settlement.usuarioId(), settlement.jugadorId());
        TenenciaMercadoEntity tenencia = tenencias
                .findByUsuarioIdAndJugadorIdForUpdate(settlement.usuarioId(), settlement.jugadorId())
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo obtener la tenencia canónica."));

        cuentaUsuario.debitar(settlement.montoTotal(), settlement.confirmadoEn());
        cuentaSuperuser.acreditar(settlement.montoTotal(), settlement.confirmadoEn());
        emision.decrementarInventario(settlement.cantidad(), settlement.confirmadoEn());
        tenencia.aplicarCompra(settlement.cantidad(), settlement.montoTotal(),
                settlement.confirmadoEn());

        UUID transaccionId = UUID.randomUUID();
        TransaccionConfirmadaMercadoEntity transaccion = new TransaccionConfirmadaMercadoEntity(
                transaccionId, settlement.usuarioId(), settlement.jugadorId(),
                settlement.nombreJugadorSnapshot(), PURCHASE, settlement.cantidad(),
                settlement.precioUnitario(), settlement.montoTotal(), settlement.contraparteId(),
                settlement.quoteId(), settlement.versionCotizacion(), settlement.solicitudId(),
                null, settlement.confirmadoEn());
        transacciones.save(transaccion);

        movimientos.saveAll(List.of(
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, CREDIT,
                        cuentaUsuario.getId(), null, null, null,
                        settlement.montoTotal().negate(), settlement.confirmadoEn()),
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, CREDIT,
                        cuentaSuperuser.getId(), null, null, null,
                        settlement.montoTotal(), settlement.confirmadoEn()),
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, TOKEN,
                        null, settlement.usuarioId(), null, settlement.jugadorId(),
                        BigDecimal.valueOf(settlement.cantidad()), settlement.confirmadoEn()),
                new MovimientoMercadoEntity(UUID.randomUUID(), transaccionId, TOKEN,
                        null, null, settlement.contraparteId(), settlement.jugadorId(),
                        BigDecimal.valueOf(settlement.cantidad()).negate(),
                        settlement.confirmadoEn())));
        return transaccionId;
    }

    private void validarSettlement(MarketPurchaseSettlement settlement) {
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
                    "Los datos de liquidación de la compra no son válidos.");
        }
        BigDecimal esperado;
        try {
            esperado = settlement.precioUnitario()
                    .multiply(BigDecimal.valueOf(settlement.cantidad()))
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El importe de la compra no es representable.");
        }
        if (esperado.compareTo(settlement.montoTotal()) != 0) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El importe total no coincide con la cotización.");
        }
    }
}
