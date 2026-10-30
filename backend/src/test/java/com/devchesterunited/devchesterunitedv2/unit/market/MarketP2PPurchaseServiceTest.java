package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseSettlement;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseTarget;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2PAccountingPersistenceService;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2PPurchasePersistenceServiceImpl;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AsientoP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AsientoP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TradeP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TradeP2PEntity;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketP2PPurchaseServiceTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_OFFER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SELLER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID REQUEST_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void comandoValidoConservaOfertaCantidadYCotizacionObservada() {
        MarketP2PPurchaseCommand command = command(OFFER_ID, 3L, 1L);

        command.validar();

        assertThat(command.idempotencyIdentity().offerId()).isEqualTo(OFFER_ID);
        assertThat(command.idempotencyIdentity().quantity()).isEqualTo(3L);
        assertThat(command.idempotencyIdentity().quote().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(command.idempotencyIdentity().quote().version()).isEqualTo(1L);
    }

    @Test
    void comandoInvalidoRechazaCantidadOfertaYCotizacion() {
        assertThatThrownBy(() -> command(null, 0L, 0L).validar())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashP2PIncluyeOfertaYPermiteReplaySoloConMismaIdentidad() {
        MarketP2PIdempotencyRequest first = request(OFFER_ID, 3L, 1L);
        MarketP2PIdempotencyRequest equivalent = request(OFFER_ID, 3L, 1L);
        MarketP2PIdempotencyRequest otherOffer = request(OTHER_OFFER_ID, 3L, 1L);

        assertThat(MarketIdempotencyParameterHasher.hash(first))
                .isEqualTo(MarketIdempotencyParameterHasher.hash(equivalent));
        assertThat(MarketIdempotencyParameterHasher.hash(first))
                .isNotEqualTo(MarketIdempotencyParameterHasher.hash(otherOffer));
    }

    @Test
    void hashP2PDistingueCantidadYVersionDeCotizacion() {
        String base = MarketIdempotencyParameterHasher.hash(request(OFFER_ID, 3L, 1L));
        String otherQuantity = MarketIdempotencyParameterHasher.hash(request(OFFER_ID, 4L, 1L));
        String otherVersion = MarketIdempotencyParameterHasher.hash(request(OFFER_ID, 3L, 2L));

        assertThat(base).isNotEqualTo(otherQuantity).isNotEqualTo(otherVersion);
    }

    @Test
    void compraParcialConservaLaCantidadSolicitadaYLaCotizacionOficial() {
        MarketP2PPurchaseCommand command = command(OFFER_ID, 2L, 1L);

        command.validar();

        assertThat(command.quantity()).isLessThan(4L);
        assertThat(command.quote().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(command.quote().version()).isEqualTo(1L);
    }

    @Test
    void compraCompletaAceptaLaCantidadExactaDelRemanente() {
        MarketP2PPurchaseCommand command = command(OFFER_ID, 4L, 1L);

        command.validar();

        assertThat(command.quantity()).isEqualTo(4L);
    }

    @Test
    void compraRechazaCantidadSuperiorAlRemanente() {
        MarketP2PPurchaseCommand command = command(OFFER_ID, 5L, 1L);

        assertThat(command.quantity()).isGreaterThan(4L);
    }

    @Test
    void compraPropiaSeIdentificaPorLaIdentidadDelComando() {
        MarketP2PPurchaseCommand command = command(OFFER_ID, 1L, 1L);

        assertThat(command.usuarioId()).isEqualTo(USER_ID);
        assertThat(command.offerId()).isEqualTo(OFFER_ID);
    }

    @Test
    void cotizacionVersionadaEsParteObligatoriaDeLaCompra() {
        assertThatThrownBy(() -> command(OFFER_ID, 1L, null).validar())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saldoInsuficienteRechazaCompraSinLiquidarNiMutarContabilidad() {
        CuentaCreditosMercadoRepository cuentas = mock(CuentaCreditosMercadoRepository.class);
        TenenciaMercadoRepository tenencias = mock(TenenciaMercadoRepository.class);
        OfertaVentaP2PRepository ofertas = mock(OfertaVentaP2PRepository.class);
        TradeP2PRepository trades = mock(TradeP2PRepository.class);
        AsientoP2PRepository asientos = mock(AsientoP2PRepository.class);
        MarketP2PAccountingPersistenceService accounting = mock(
                MarketP2PAccountingPersistenceService.class);
        MarketP2PPurchasePersistenceServiceImpl persistence = new MarketP2PPurchasePersistenceServiceImpl(
                cuentas, tenencias, ofertas, trades, asientos, accounting);

        OfertaVentaP2PEntity offer = mock(OfertaVentaP2PEntity.class);
        CuentaCreditosMercadoEntity buyerAccount = mock(CuentaCreditosMercadoEntity.class);
        CuentaCreditosMercadoEntity sellerAccount = mock(CuentaCreditosMercadoEntity.class);
        TenenciaMercadoEntity buyerHolding = mock(TenenciaMercadoEntity.class);
        TenenciaMercadoEntity sellerHolding = mock(TenenciaMercadoEntity.class);
        when(ofertas.findByIdForUpdate(OFFER_ID)).thenReturn(Optional.of(offer));
        when(offer.getEstado()).thenReturn(OfertaVentaP2PEntity.OPEN);
        when(offer.getJugadorId()).thenReturn(PLAYER_ID);
        when(offer.getVendedorId()).thenReturn(SELLER_ID);
        when(offer.getCantidadRestante()).thenReturn(4L);
        when(cuentas.findByUsuarioIdForUpdate(USER_ID)).thenReturn(Optional.of(buyerAccount));
        when(cuentas.findByUsuarioIdForUpdate(SELLER_ID)).thenReturn(Optional.of(sellerAccount));
        when(buyerAccount.getBalance()).thenReturn(new BigDecimal("1.00"));
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(buyerHolding));
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(SELLER_ID, PLAYER_ID))
                .thenReturn(Optional.of(sellerHolding));

        assertThatThrownBy(() -> persistence.liquidar(settlement()))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INSUFFICIENT_BALANCE));

        verify(buyerAccount, never()).debitar(any(), any());
        verify(sellerAccount, never()).acreditar(any(), any());
        verify(buyerHolding, never()).aplicarCompra(any(), any(), any());
        verify(sellerHolding, never()).consumirReserva(any(), any(), any());
        verify(offer, never()).actualizarCantidades(any(), any(), any(), any(), any());
        verify(trades, never()).saveAndFlush(any(TradeP2PEntity.class));
        verify(accounting, never()).persistir(any(), any(), any());
        verify(asientos, never()).saveAll(any());
    }

    private static MarketP2PPurchaseSettlement settlement() {
        return new MarketP2PPurchaseSettlement(REQUEST_ID, USER_ID,
                new MarketP2PPurchaseTarget(OFFER_ID, PLAYER_ID, "Player"),
                new QuoteVersion(QUOTE_ID, 1L, PLAYER_ID, new BigDecimal("2.50"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"), null,
                        QuoteValidityStatus.VALID, "TEST"), 1L,
                Instant.parse("2026-01-01T00:00:01Z"));
    }

    private static MarketP2PPurchaseCommand command(UUID offerId, Long quantity, Long version) {
        return new MarketP2PPurchaseCommand(USER_ID, "purchase-key", offerId, PLAYER_ID,
                quantity, new MarketP2PQuoteIdentity(QUOTE_ID, version));
    }

    private static MarketP2PIdempotencyRequest request(UUID offerId, Long quantity,
            Long version) {
        return new MarketP2PIdempotencyRequest(USER_ID, "purchase-key",
                new MarketP2PPurchaseIdentity(offerId, PLAYER_ID, quantity,
                        new MarketP2PQuoteIdentity(QUOTE_ID, version)));
    }
}
