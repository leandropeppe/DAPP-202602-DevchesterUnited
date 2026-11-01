package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaCancelacionOfertaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AuditoriaCancelacionOfertaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2PCancellationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketP2PCancellationServiceTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUEST_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID AUDIT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Mock
    private MarketIdempotencyService idempotencia;
    @Mock
    private EstadoMercadoRepository estados;
    @Mock
    private OfertaVentaP2PRepository ofertas;
    @Mock
    private TenenciaMercadoRepository tenencias;
    @Mock
    private AuditoriaCancelacionOfertaP2PRepository auditorias;

    private MarketP2PCancellationServiceImpl service;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new MarketP2PCancellationServiceImpl(idempotencia, estados, ofertas,
                tenencias, auditorias, clock);
        lenient().when(idempotencia.reclamarConPropiedad(any(MarketP2PCancelCommand.class)))
                .thenReturn(ownerClaim());
        lenient().when(estados.findSingletonForRead()).thenReturn(Optional.of(readyMarket()));
    }

    @Test
    void cancelaOfertaOpenYLiberaTodoElRemanente() {
        OfertaVentaP2PEntity offer = offer(10L, 10L, 0L, 0L, OfertaVentaP2PEntity.OPEN);
        TenenciaMercadoEntity holding = holding(10L, 10L);
        prepare(offer, holding, 0L);

        MarketP2PCancelResult result = service.cancelar(command());

        assertThat(result.offerId()).isEqualTo(OFFER_ID);
        assertThat(result.originalQuantity()).isEqualTo(10L);
        assertThat(result.soldQuantity()).isZero();
        assertThat(result.releasedQuantity()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo(OfertaVentaP2PEntity.CANCELLED);
        assertThat(offer.getCantidadRestante()).isZero();
        assertThat(offer.getCantidadLiberada()).isEqualTo(10L);
        assertThat(holding.getCantidadReservada()).isZero();
    }

    @Test
    void cancelaOfertaParcialSinRevertirLoVendido() {
        OfertaVentaP2PEntity offer = offer(10L, 4L, 6L, 0L,
                OfertaVentaP2PEntity.PARTIALLY_FILLED);
        TenenciaMercadoEntity holding = holding(10L, 4L);
        prepare(offer, holding, 0L);

        service.cancelar(command());

        assertThat(offer.getCantidadRestante()).isZero();
        assertThat(offer.getCantidadVendida()).isEqualTo(6L);
        assertThat(offer.getCantidadLiberada()).isEqualTo(4L);
        assertThat(holding.getQuantity()).isEqualTo(10L);
        assertThat(holding.getCantidadReservada()).isZero();
        verify(auditorias).saveAndFlush(any(AuditoriaCancelacionOfertaP2PEntity.class));
    }

    @Test
    void rechazaOfertaDeOtroVendedorSinBloquearNiLiberarTenencia() {
        when(ofertas.findByIdAndVendedorIdForUpdate(OFFER_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(command()))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.OFFER_NOT_FOUND));

        verify(tenencias, never()).findByUsuarioIdAndJugadorIdForUpdate(any(), any());
        verify(auditorias, never()).saveAndFlush(any());
    }

    @Test
    void rechazaOfertaFilled() {
        OfertaVentaP2PEntity offer = offer(10L, 0L, 10L, 0L, OfertaVentaP2PEntity.FILLED);
        when(ofertas.findByIdAndVendedorIdForUpdate(OFFER_ID, USER_ID))
                .thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.cancelar(command()))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE));

        verify(tenencias, never()).findByUsuarioIdAndJugadorIdForUpdate(any(), any());
        verify(auditorias, never()).saveAndFlush(any());
    }

    @Test
    void rechazaOfertaCancelled() {
        OfertaVentaP2PEntity offer = offer(10L, 0L, 6L, 4L,
                OfertaVentaP2PEntity.CANCELLED);
        when(ofertas.findByIdAndVendedorIdForUpdate(OFFER_ID, USER_ID))
                .thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.cancelar(command()))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.OFFER_ALREADY_CANCELLED));

        verify(tenencias, never()).findByUsuarioIdAndJugadorIdForUpdate(any(), any());
        verify(auditorias, never()).saveAndFlush(any());
    }

    @Test
    void replayDevuelveLaAuditoriaOriginalSinLeerOfertaMutable() {
        AuditoriaCancelacionOfertaP2PEntity audit = audit(10L, 6L, 4L);
        MarketIdempotencyResult replay = new MarketIdempotencyResult(REQUEST_ID, USER_ID,
                "cancel-key", "P2P_CANCEL", "hash", MarketIdempotencyStatus.CONFIRMED,
                null, AUDIT_ID, NOW);
        when(idempotencia.reclamarConPropiedad(any(MarketP2PCancelCommand.class)))
                .thenReturn(new MarketIdempotencyClaim(replay, false));
        when(auditorias.findById(AUDIT_ID)).thenReturn(Optional.of(audit));

        MarketP2PCancelResult result = service.cancelar(command());

        assertThat(result.originalQuantity()).isEqualTo(10L);
        assertThat(result.soldQuantity()).isEqualTo(6L);
        assertThat(result.releasedQuantity()).isEqualTo(4L);
        assertThat(result.status()).isEqualTo(OfertaVentaP2PEntity.CANCELLED);
        verify(estados, never()).findSingletonForRead();
        verify(ofertas, never()).findByIdAndVendedorIdForUpdate(any(), any());
        verify(tenencias, never()).findByUsuarioIdAndJugadorIdForUpdate(any(), any());
    }

    private void prepare(OfertaVentaP2PEntity offer, TenenciaMercadoEntity holding,
            long activeRemaining) {
        when(ofertas.findByIdAndVendedorIdForUpdate(OFFER_ID, USER_ID))
                .thenReturn(Optional.of(offer));
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(holding));
        when(ofertas.sumRemainingActiveByVendedorIdAndJugadorId(USER_ID, PLAYER_ID))
                .thenReturn(activeRemaining);
        when(auditorias.saveAndFlush(any(AuditoriaCancelacionOfertaP2PEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencia.confirmar(eq(REQUEST_ID), eq(TOKEN_ID), any(UUID.class)))
                .thenAnswer(invocation -> new MarketIdempotencyResult(REQUEST_ID, USER_ID,
                        "cancel-key", "P2P_CANCEL", "hash", MarketIdempotencyStatus.CONFIRMED,
                        null, invocation.getArgument(2), NOW));
    }

    private OfertaVentaP2PEntity offer(long original, long remaining, long sold,
            long released, String status) {
        return new OfertaVentaP2PEntity(OFFER_ID, USER_ID, PLAYER_ID,
                new OfertaVentaP2PEntity.Cantidades(original, remaining, sold, released),
                status, new OfertaVentaP2PEntity.Auditoria(REQUEST_ID, NOW, NOW));
    }

    private TenenciaMercadoEntity holding(long quantity, long reserved) {
        return new TenenciaMercadoEntity(new TenenciaMercadoId(USER_ID, PLAYER_ID), null, null,
                new TenenciaMercadoEntity.Estado(quantity, reserved,
                        BigDecimal.ZERO.setScale(8), NOW));
    }

    private AuditoriaCancelacionOfertaP2PEntity audit(long original, long sold,
            long released) {
        return new AuditoriaCancelacionOfertaP2PEntity(
                new AuditoriaCancelacionOfertaP2PEntity.Identidad(
                        AUDIT_ID, OFFER_ID, USER_ID, REQUEST_ID),
                new AuditoriaCancelacionOfertaP2PEntity.Cantidades(original, sold, released),
                NOW);
    }

    private MarketP2PCancelCommand command() {
        return new MarketP2PCancelCommand(USER_ID, OFFER_ID, "cancel-key");
    }

    private MarketIdempotencyClaim ownerClaim() {
        return new MarketIdempotencyClaim(new MarketIdempotencyResult(REQUEST_ID, USER_ID,
                "cancel-key", "P2P_CANCEL", "hash", MarketIdempotencyStatus.PROCESSING,
                TOKEN_ID, null, NOW), true);
    }

    private EstadoMercadoEntity readyMarket() {
        return EstadoMercadoEntity.builder().id((short) 1)
                .status(EstadoMercado.READY.name()).epoch(1L)
                .reservaInicialConfigurada(null).reservaInicialInicializada(null)
                .motivoFallo(null).creadoEn(NOW).actualizadoEn(NOW).build();
    }

    private static final UUID TOKEN_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
}
