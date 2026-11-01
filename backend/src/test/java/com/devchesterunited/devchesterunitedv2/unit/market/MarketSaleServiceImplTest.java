package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketSaleServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketQuoteValidationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSalePersistenceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MarketSaleServiceImplTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID QUOTE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SUPERUSER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID TRANSACTION_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock private EstadoMercadoRepository estados;
    @Mock private JugadorRepository jugadores;
    @Mock private CabeceraCotizacionMercadoRepository cabeceras;
    @Mock private MarketQuoteValidationService cotizaciones;
    @Mock private ContraparteMercadoRepository contrapartes;
    @Mock private MarketSalePersistenceService persistencia;
    @Mock private TransaccionConfirmadaMercadoRepository transacciones;

    private FakeIdempotency idempotencia;
    private MarketSaleService service;

    @BeforeEach
    void setUp() {
        idempotencia = new FakeIdempotency();
        service = new MarketSaleServiceImpl(idempotencia, estados, jugadores, cabeceras,
                cotizaciones, contrapartes, persistencia, transacciones,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rechazaSolicitudNulaYCantidadInvalidaAntesDeReclamarIdempotencia() {
        assertThatThrownBy(() -> service.vender(null))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.INVALID_INPUT));

        assertThatThrownBy(() -> service.vender(request(0L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> service.vender(request(-1L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.INVALID_INPUT));
        assertThat(idempotencia.claims).isZero();
    }

    @Test
    void rechazaVentaCuandoMercadoNoEstaPreparado() {
        givenBaseRequest();
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado("FAILED")));

        assertReason(TokenMarketException.Reason.MARKET_NOT_READY);
        verify(persistencia, never()).persistirVenta(any());
    }

    @Test
    void rechazaJugadorInactivo() {
        givenMarket("READY");
        when(jugadores.findByIdForRead(PLAYER_ID)).thenReturn(Optional.of(
                new JugadorEntity(PLAYER_ID, "Jugador", UUID.randomUUID(), "FW", 9, "AR", false)));

        assertReason(TokenMarketException.Reason.INACTIVE_PLAYER);
        verify(persistencia, never()).persistirVenta(any());
    }

    @Test
    void rechazaCotizacionAusenteVencidaInvalidadaReemplazadaOVersionIncorrecta() {
        givenMarket("READY");
        when(jugadores.findByIdForRead(PLAYER_ID)).thenReturn(Optional.of(activePlayer()));
        when(cabeceras.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(
                new CabeceraCotizacionMercadoEntity(PLAYER_ID, QUOTE_ID, 2L, NOW)));
        when(cotizaciones.validar(PLAYER_ID, QUOTE_ID, 1L)).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.QUOTE_UNAVAILABLE, "quote"));

        assertReason(TokenMarketException.Reason.QUOTE_UNAVAILABLE);
        verify(persistencia, never()).persistirVenta(any());
    }

    @Test
    void aceptaUnicamenteQuoteIdYVersionExactos() {
        givenValidSale();
        when(cotizaciones.validar(PLAYER_ID, QUOTE_ID, 1L)).thenReturn(
                new QuoteVersion(QUOTE_ID, 1L, PLAYER_ID, new BigDecimal("2.00"),
                        NOW, NOW, null, QuoteValidityStatus.VALID, "test"));
        when(contrapartes.findByTipoContraparte("SUPERUSER")).thenReturn(Optional.of(
                new ContraparteMercadoEntity(SUPERUSER_ID, "SUPERUSER", "Test superuser", NOW)));
        when(persistencia.persistirVenta(any())).thenReturn(TRANSACTION_ID);

        MarketSaleResult result = service.vender(request(2L));

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        verify(cotizaciones).validar(PLAYER_ID, QUOTE_ID, 1L);
    }

    @Test
    void propagaReservaInsuficienteSinConfirmarLaVenta() {
        givenValidSale();
        when(persistencia.persistirVenta(any())).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_BALANCE, "reserve"));

        assertReason(TokenMarketException.Reason.INSUFFICIENT_BALANCE);
        assertThat(idempotencia.confirmations).isZero();
    }

    @Test
    void usaOperacionSaleYRecuperaReintentoConfirmadoSinPersistirOtraVez() {
        givenValidSale();
        when(persistencia.persistirVenta(any())).thenReturn(TRANSACTION_ID);

        MarketSaleResult first = service.vender(request(2L));
        when(transacciones.findByIdAndUsuarioId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(
                confirmedSaleTransaction()));
        MarketSaleResult retry = service.vender(request(2L));

        assertThat(first.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(retry.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(idempotencia.lastRequest.tipoOperacion()).isEqualTo("SALE");
        verify(persistencia).persistirVenta(any());
    }

    @Test
    void rechazaConflictosDeClavePorParametrosOTipoSinConfirmar() {
        idempotencia.putConfirmed("sale-key", "quantity=2", "SALE");

        assertThatThrownBy(() -> service.vender(new MarketSaleRequest(USER_ID, "sale-key",
                PLAYER_ID, 3L, QUOTE_ID, 1L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
        assertThatThrownBy(() -> idempotencia.reclamarConPropiedad(new MarketIdempotencyRequest(
                USER_ID, "sale-key", "PURCHASE", PLAYER_ID, 2L, QUOTE_ID, 1L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rechazaVentaSuperiorALaTenenciaSinConfirmar() {
        givenValidSale();
        when(persistencia.persistirVenta(any())).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_INVENTORY, "holding"));

        assertReason(TokenMarketException.Reason.INSUFFICIENT_INVENTORY);
        assertThat(idempotencia.confirmations).isZero();
    }

    private void givenValidSale() {
        givenMarket("READY");
        when(jugadores.findByIdForRead(PLAYER_ID)).thenReturn(Optional.of(activePlayer()));
        when(cabeceras.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(
                new CabeceraCotizacionMercadoEntity(PLAYER_ID, QUOTE_ID, 1L, NOW)));
        when(cotizaciones.validar(PLAYER_ID, QUOTE_ID, 1L)).thenReturn(
                new QuoteVersion(QUOTE_ID, 1L, PLAYER_ID, new BigDecimal("2.00"),
                        NOW, NOW, null, QuoteValidityStatus.VALID, "test"));
        when(contrapartes.findByTipoContraparte("SUPERUSER")).thenReturn(Optional.of(
                new ContraparteMercadoEntity(SUPERUSER_ID, "SUPERUSER", "Test superuser", NOW)));
    }

    private void givenBaseRequest() {
        idempotencia.claims = 0;
    }

    private void givenMarket(String status) {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado(status)));
    }

    private JugadorEntity activePlayer() {
        return new JugadorEntity(PLAYER_ID, "Jugador", UUID.randomUUID(), "FW", 9, "AR", true);
    }

    private EstadoMercadoEntity estado(String status) {
        return EstadoMercadoEntity.builder().id((short) 1).status(status).epoch(2L)
                .reservaInicialConfigurada(new BigDecimal("100"))
                .reservaInicialInicializada(new BigDecimal("100")).motivoFallo(null)
                .creadoEn(NOW).actualizadoEn(NOW).build();
    }

    private MarketSaleRequest request(Long quantity) {
        return new MarketSaleRequest(USER_ID, "sale-key", PLAYER_ID, quantity, QUOTE_ID, 1L);
    }

    private TransaccionConfirmadaMercadoEntity confirmedSaleTransaction() {
        return TransaccionConfirmadaMercadoEntity.builder().id(TRANSACTION_ID).usuarioId(USER_ID)
                .jugadorId(PLAYER_ID).nombreJugadorSnapshot("Jugador").tipoOperacion("SALE")
                .quantity(2L).precioUnitario(new BigDecimal("2.00"))
                .montoTotal(new BigDecimal("4.00")).contraparteId(SUPERUSER_ID)
                .quoteId(QUOTE_ID).versionCotizacion(1L)
                .solicitudIdempotenciaId(idempotencia.requestId())
                .pnlRealizado(new BigDecimal("1.00")).confirmadoEn(NOW).build();
    }

    private void assertReason(TokenMarketException.Reason reason) {
        assertThatThrownBy(() -> service.vender(request(2L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(reason));
    }

    private static final class FakeIdempotency implements MarketIdempotencyService {
        private final Map<String, MarketIdempotencyResult> states = new HashMap<>();
        private int claims;
        private int confirmations;
        private MarketIdempotencyRequest lastRequest;

        UUID requestId() {
            return states.get("sale-key").solicitudId();
        }

        @Override public MarketIdempotencyResult reclamar(MarketIdempotencyRequest request) {
            return reclamarConPropiedad(request).resultado();
        }

        @Override
        public MarketIdempotencyClaim reclamarConPropiedad(MarketIdempotencyRequest request) {
            claims++;
            lastRequest = request;
            MarketIdempotencyResult existing = states.get(request.claveIdempotencia());
            if (existing != null && (!existing.hashParametros().equals(hash(request))
                    || !existing.tipoOperacion().equals(request.tipoOperacion()))) {
                throw new TokenMarketException(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT,
                        "conflict");
            }
            if (existing != null) return new MarketIdempotencyClaim(existing, false);
            MarketIdempotencyResult result = new MarketIdempotencyResult(UUID.randomUUID(),
                    USER_ID, request.claveIdempotencia(), request.tipoOperacion(), hash(request),
                    MarketIdempotencyStatus.PROCESSING, UUID.randomUUID(), null, NOW);
            states.put(request.claveIdempotencia(), result);
            return new MarketIdempotencyClaim(result, true);
        }

        @Override
        public MarketIdempotencyResult confirmar(UUID id, UUID token, UUID reference) {
            confirmations++;
            MarketIdempotencyResult previous = states.get("sale-key");
            MarketIdempotencyResult result = new MarketIdempotencyResult(id, USER_ID, "sale-key",
                    "SALE", previous.hashParametros(), MarketIdempotencyStatus.CONFIRMED, null,
                    reference, NOW);
            states.put("sale-key", result);
            return result;
        }

        @Override public MarketIdempotencyResult rechazar(UUID id, UUID token) { return null; }
        @Override public MarketIdempotencyResult marcarIncierta(UUID id, UUID token) { return null; }
        @Override public Optional<MarketIdempotencyResult> recuperar(UUID user, String key) {
            return Optional.ofNullable(states.get(key));
        }

        void putConfirmed(String key, String hash, String operation) {
            states.put(key, new MarketIdempotencyResult(UUID.randomUUID(), USER_ID, key, operation,
                    hash, MarketIdempotencyStatus.CONFIRMED, null, TRANSACTION_ID, NOW));
        }

        private String hash(MarketIdempotencyRequest request) {
            return MarketIdempotencyParameterHasher.hash(request);
        }
    }
}
