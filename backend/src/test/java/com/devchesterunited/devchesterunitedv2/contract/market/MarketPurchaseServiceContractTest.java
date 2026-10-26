package com.devchesterunited.devchesterunitedv2.contract.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T043 contract suite. A concrete implementation is intentionally supplied by
 * T045/T046; these tests define the service behavior without implementing
 * account, inventory, holding or movement mutations prematurely.
 */
public abstract class MarketPurchaseServiceContractTest {
    protected static final UUID USER_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    protected static final UUID PLAYER_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    protected static final UUID QUOTE_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");

    protected abstract MarketPurchaseService service();

    /** The real idempotency service used to seed a durable competing state. */
    protected abstract MarketIdempotencyService idempotency();

    /** Concrete tests provide valid persisted market fixtures. */
    protected abstract void givenValidPurchase();

    /** Fixture with the same invariants but a price suitable for the precision case. */
    protected void givenPrecisionPurchase() {
        givenValidPurchase();
    }

    /** Allows a concrete test to expose the durable transaction for a retry. */
    protected void givenConfirmedRecovery() { }

    protected abstract void givenInactivePlayer();
    protected abstract void givenMarketNotReady();
    protected abstract void givenMissingAccount();
    protected abstract void givenInsufficientBalance();
    protected abstract void givenInsufficientInventory();
    protected abstract void givenUnavailableQuote();
    protected abstract void givenRejectedOperation();
    protected abstract void givenUncertainOperation();

    /** Must assert that no account, issuance, holding, movement or history changed. */
    protected abstract void assertNoPurchaseEffects();

    @Test
    void compraValidaTransfiereElImporteExactoYDevuelveConfirmada() {
        givenValidPurchase();

        MarketPurchaseResult result = service().comprar(request(3L));

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(result.cantidad()).isEqualTo(3L);
        assertThat(result.precioUnitario()).isEqualByComparingTo("1.25");
        assertThat(result.montoTotal()).isEqualByComparingTo("3.75");
        assertThat(result.usuarioId()).isEqualTo(USER_ID);
        assertThat(result.jugadorId()).isEqualTo(PLAYER_ID);
        assertThat(result.solicitudId()).isNotNull();
        assertThat(result.transaccionId()).isNotNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L, 101L, Long.MAX_VALUE})
    void cantidadNoValidaNoPuedeProducirUnaCompra(Long cantidad) {
        var purchaseService = service();
        var purchaseRequest = request(cantidad);
        assertThatThrownBy(() -> purchaseService.comprar(purchaseRequest))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INVALID_INPUT));
        assertNoPurchaseEffects();
    }

    @Test
    void jugadorInactivoRechazaSinEfectosParciales() {
        givenInactivePlayer();

        assertRejectedWithoutEffects();
    }

    @Test
    void mercadoNoDisponibleRechazaSinEfectosParciales() {
        givenMarketNotReady();

        var purchaseService = service();
        var purchaseRequest = request(1L);
        assertThatThrownBy(() -> purchaseService.comprar(purchaseRequest))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.MARKET_NOT_READY));
        assertNoPurchaseEffects();
    }

    @Test
    void cuentaInexistenteRechazaSinEfectosParciales() {
        givenMissingAccount();

        var purchaseService = service();
        var purchaseRequest = request(1L);
        assertThatThrownBy(() -> purchaseService.comprar(purchaseRequest))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.ACCOUNT_NOT_FOUND));
        assertNoPurchaseEffects();
    }

    @Test
    void saldoInsuficienteRechazaSinEfectosParciales() {
        givenInsufficientBalance();

        var purchaseService = service();
        var purchaseRequest = request(2L);
        assertThatThrownBy(() -> purchaseService.comprar(purchaseRequest))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INSUFFICIENT_BALANCE));
        assertNoPurchaseEffects();
    }

    @Test
    void inventarioInsuficienteRechazaSinEfectosParciales() {
        givenInsufficientInventory();

        var purchaseService = service();
        var purchaseRequest = request(2L);
        assertThatThrownBy(() -> purchaseService.comprar(purchaseRequest))
                .isInstanceOf(TokenMarketException.class);
        assertNoPurchaseEffects();
    }

    @Test
    void cotizacionInexistenteHistoricaInvalidadaOVencidaRechazaSinEfectos() {
        givenUnavailableQuote();

        var purchaseService = service();
        var purchaseRequest = request(1L);
        assertThatThrownBy(() -> purchaseService.comprar(purchaseRequest))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.QUOTE_UNAVAILABLE));
        assertNoPurchaseEffects();
    }

    @Test
    void reintentoIdenticoRecuperaElResultadoConfirmadoSinDuplicar() {
        givenValidPurchase();
        MarketPurchaseRequest request = request(2L);

        MarketPurchaseResult first = service().comprar(request);
        givenConfirmedRecovery();
        MarketPurchaseResult retry = service().comprar(request);

        assertThat(first.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(retry).usingRecursiveComparison().isEqualTo(first);
    }

    @Test
    void mismaClaveConParametrosDiferentesEsConflicto() {
        givenValidPurchase();
        service().comprar(request(1L));

        assertThatThrownBy(() -> service().comprar(request(2L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
        assertNoPurchaseEffects();
    }

    @Test
    void processingDeOtraEjecucionNoRepiteLaCompra() {
        idempotency().reclamar(idempotencyRequest(1L));

        MarketPurchaseResult result = service().comprar(request(1L));

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.PROCESSING);
        assertThat(result.transaccionId()).isNull();
        assertNoPurchaseEffects();
    }

    @Test
    void rejectedNoVuelveAEjecutarLaCompra() {
        givenRejectedOperation();

        MarketPurchaseResult result = service().comprar(request(1L));

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.REJECTED);
        assertThat(result.transaccionId()).isNull();
        assertNoPurchaseEffects();
    }

    @Test
    void uncertainNoRepiteMovimientosSinReconciliacion() {
        givenUncertainOperation();

        MarketPurchaseResult result = service().comprar(request(1L));

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.UNCERTAIN);
        assertThat(result.transaccionId()).isNull();
        assertNoPurchaseEffects();
    }

    @Test
    void importeMonetarioDebeMantenerPrecisionSinDesbordamiento() {
        givenPrecisionPurchase();

        MarketPurchaseResult result = service().comprar(request(7L));

        assertThat(result.montoTotal()).isEqualByComparingTo(new BigDecimal("2.03"));
    }

    private void assertRejectedWithoutEffects() {
        assertThatThrownBy(() -> service().comprar(request(1L)))
                .isInstanceOf(TokenMarketException.class);
        assertNoPurchaseEffects();
    }

    private MarketPurchaseRequest request(Long quantity) {
        return new MarketPurchaseRequest(USER_ID, "purchase-key", PLAYER_ID,
                quantity, QUOTE_ID, 1L);
    }

    private MarketIdempotencyRequest idempotencyRequest(long quantity) {
        return new MarketIdempotencyRequest(USER_ID, "purchase-key", "PURCHASE",
                PLAYER_ID, quantity, QUOTE_ID, 1L);
    }
}
