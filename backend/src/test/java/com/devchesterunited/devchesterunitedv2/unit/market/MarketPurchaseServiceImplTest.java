package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;

import com.devchesterunited.devchesterunitedv2.contract.market.MarketPurchaseServiceContractTest;
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
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketPurchaseServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchasePersistenceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketQuoteValidationService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketPurchaseServiceImplTest extends MarketPurchaseServiceContractTest {
    private static final UUID SUPERUSER_ID = UUID.fromString(
            "dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID TRANSACTION_ID = UUID.fromString(
            "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock private EstadoMercadoRepository estados;
    @Mock private JugadorRepository jugadores;
    @Mock private CabeceraCotizacionMercadoRepository cabeceras;
    @Mock private MarketQuoteValidationService cotizaciones;
    @Mock private ContraparteMercadoRepository contrapartes;
    @Mock private MarketPurchasePersistenceService persistencia;
    @Mock private TransaccionConfirmadaMercadoRepository transacciones;

    private FakeIdempotency idempotencia;
    private MarketPurchaseServiceImpl service;
    private int purchasePersistenceCalls;
    private RuntimeException persistenceFailure;

    @BeforeEach
    void setUp() {
        idempotencia = new FakeIdempotency();
        purchasePersistenceCalls = 0;
        persistenceFailure = null;
        service = new MarketPurchaseServiceImpl(idempotencia, estados, jugadores, cabeceras,
                cotizaciones, contrapartes, persistencia, transacciones,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Override
    protected com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService service() {
        return service;
    }

    @Override
    protected MarketIdempotencyService idempotency() {
        return idempotencia;
    }

    @Override
    protected void givenValidPurchase() {
        configureMarketAndPlayer();
        configureQuoteHeader();
        configureQuote(new BigDecimal("1.25"));
        configureSettlement();
    }

    @Override
    protected void givenPrecisionPurchase() {
        configureMarketAndPlayer();
        configureQuoteHeader();
        configureQuote(new BigDecimal("0.29"));
        configureSettlement();
    }

    @Override
    protected void givenConfirmedRecovery() {
        when(transacciones.findByIdAndUsuarioId(any(), any())).thenReturn(Optional.of(
                transaction()));
    }

    @Override
    protected void givenInactivePlayer() {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado("READY")));
        when(jugadores.findByIdForRead(PLAYER_ID)).thenReturn(Optional.empty());
    }

    @Override
    protected void givenMarketNotReady() {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado("FAILED")));
    }

    @Override
    protected void givenMissingAccount() {
        persistenceFailure = new TokenMarketException(
                TokenMarketException.Reason.ACCOUNT_NOT_FOUND, "missing");
        givenValidPurchase();
    }

    @Override
    protected void givenInsufficientBalance() {
        persistenceFailure = new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_BALANCE, "balance");
        givenValidPurchase();
    }

    @Override
    protected void givenInsufficientInventory() {
        persistenceFailure = new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_INVENTORY, "inventory");
        givenValidPurchase();
    }

    @Override
    protected void givenUnavailableQuote() {
        configureMarketAndPlayer();
        when(cabeceras.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.empty());
        when(cotizaciones.validar(PLAYER_ID, QUOTE_ID, 1L)).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.QUOTE_UNAVAILABLE, "quote"));
    }

    @Override
    protected void givenRejectedOperation() {
        idempotencia.put(MarketIdempotencyStatus.REJECTED, null);
    }

    @Override
    protected void givenUncertainOperation() {
        idempotencia.put(MarketIdempotencyStatus.UNCERTAIN, null);
    }

    @Override
    protected void assertNoPurchaseEffects() {
        assertThat(purchasePersistenceCalls).isLessThanOrEqualTo(1);
    }

    private void configureMarketAndPlayer() {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado("READY")));
        JugadorEntity player = new JugadorEntity(PLAYER_ID, "Jugador de prueba", UUID.randomUUID(),
                "FW", 9, "AR", true);
        when(jugadores.findByIdForRead(PLAYER_ID)).thenReturn(Optional.of(player));
    }

    private void configureQuoteHeader() {
        when(cabeceras.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(
                new CabeceraCotizacionMercadoEntity(PLAYER_ID, QUOTE_ID, 1L, NOW)));
    }

    private void configureQuote(BigDecimal price) {
        when(cotizaciones.validar(PLAYER_ID, QUOTE_ID, 1L)).thenReturn(new QuoteVersion(
                QUOTE_ID, 1L, PLAYER_ID, price, NOW, NOW, null,
                QuoteValidityStatus.VALID, "test"));
    }

    private void configureSettlement() {
        when(contrapartes.findByTipoContraparte("SUPERUSER")).thenReturn(Optional.of(
                new ContraparteMercadoEntity(SUPERUSER_ID, "SUPERUSER", "Test superuser", NOW)));
        when(persistencia.persistirCompra(any())).thenAnswer(invocation -> {
            if (persistenceFailure != null) {
                throw persistenceFailure;
            }
            purchasePersistenceCalls++;
            return TRANSACTION_ID;
        });
    }

    private EstadoMercadoEntity estado(String status) {
        return new EstadoMercadoEntity((short) 1, status, 2L, new BigDecimal("100"),
                new BigDecimal("100"), null, NOW, NOW);
    }

    private TransaccionConfirmadaMercadoEntity transaction() {
        return new TransaccionConfirmadaMercadoEntity(TRANSACTION_ID, USER_ID, PLAYER_ID,
                "Jugador de prueba", "PURCHASE", 2L, new BigDecimal("1.25"),
                new BigDecimal("2.50"), SUPERUSER_ID, QUOTE_ID, 1L,
                idempotencia.requestId(), null, NOW);
    }

    private static final class FakeIdempotency implements MarketIdempotencyService {
        private final Map<String, MarketIdempotencyResult> states = new HashMap<>();
        private UUID requestId;

        @Override
        public MarketIdempotencyResult reclamar(MarketIdempotencyRequest request) {
            if (states.containsKey(request.claveIdempotencia())) {
                return states.get(request.claveIdempotencia());
            }
            MarketIdempotencyResult result = processing(request);
            states.put(request.claveIdempotencia(), result);
            return result;
        }

        @Override
        public MarketIdempotencyClaim reclamarConPropiedad(MarketIdempotencyRequest request) {
            MarketIdempotencyResult existing = states.get(request.claveIdempotencia());
            if (existing != null) {
                if (!existing.hashParametros().equals(hash(request))) {
                    throw new TokenMarketException(
                            TokenMarketException.Reason.IDEMPOTENCY_CONFLICT, "conflict");
                }
                return new MarketIdempotencyClaim(existing, false);
            }
            MarketIdempotencyResult result = processing(request);
            states.put(request.claveIdempotencia(), result);
            return new MarketIdempotencyClaim(result, true);
        }

        @Override
        public MarketIdempotencyResult confirmar(UUID id, UUID token, UUID reference) {
            MarketIdempotencyResult previous = states.get("purchase-key");
            MarketIdempotencyResult result = confirmed(id, reference,
                    previous == null ? hash(request(1L)) : previous.hashParametros());
            states.put("purchase-key", result);
            return result;
        }

        @Override public MarketIdempotencyResult rechazar(UUID id, UUID token) { return null; }
        @Override public MarketIdempotencyResult marcarIncierta(UUID id, UUID token) { return null; }

        @Override
        public Optional<MarketIdempotencyResult> recuperar(UUID user, String key) {
            return Optional.ofNullable(states.get(key));
        }

        void put(MarketIdempotencyStatus status, UUID reference) {
            requestId = UUID.randomUUID();
            states.put("purchase-key", new MarketIdempotencyResult(requestId, USER_ID,
                    "purchase-key", "PURCHASE", hash(request(1L)), status, null, reference, NOW));
        }

        UUID requestId() { return requestId; }

        MarketIdempotencyResult confirmed(UUID id, UUID reference, String hash) {
            requestId = id;
            return new MarketIdempotencyResult(id, USER_ID, "purchase-key", "PURCHASE",
                    hash,
                    MarketIdempotencyStatus.CONFIRMED, null, reference, NOW);
        }

        private MarketIdempotencyResult processing(MarketIdempotencyRequest request) {
            requestId = UUID.randomUUID();
            return new MarketIdempotencyResult(requestId, USER_ID, "purchase-key", "PURCHASE",
                    hash(request), MarketIdempotencyStatus.PROCESSING, UUID.randomUUID(), null, NOW);
        }

        private String hash(MarketIdempotencyRequest request) {
            return MarketIdempotencyParameterHasher.hash(request);
        }

        private MarketIdempotencyRequest request(long quantity) {
            return new MarketIdempotencyRequest(USER_ID, "purchase-key", "PURCHASE",
                    PLAYER_ID, quantity, QUOTE_ID, 1L);
        }
    }
}
