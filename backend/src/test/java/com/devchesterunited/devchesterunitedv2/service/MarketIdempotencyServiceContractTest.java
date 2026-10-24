package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato unitario de T032. La coordinacion advisory de T035 queda fuera de
 * este contrato y se verifica con PostgreSQL en la tarea correspondiente.
 */
abstract class MarketIdempotencyServiceContractTest {
    protected static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    protected static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    protected static final UUID RESULT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    protected static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    protected static final UUID QUOTE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    protected static final Instant START = Instant.parse("2026-10-15T12:00:00Z");

    /** T034 debe proveer esta fabrica con reloj y timeout controlables. */
    protected abstract MarketIdempotencyService service(Clock clock, Duration processingTimeout);

    @Test
    void primeraReclamacionCreaProcessing() {
        var resultado = service(fixedClock(START), Duration.ofMinutes(5))
                .reclamar(request(1L));

        assertThat(resultado.estado()).isEqualTo(MarketIdempotencyStatus.PROCESSING);
        assertThat(resultado.solicitudId()).isNotNull();
        assertThat(resultado.tokenEjecucion()).isNotNull();
        assertThat(resultado.referenciaResultado()).isNull();
    }

    @Test
    void mismaClaveYParametrosRecuperaElResultadoSinDuplicar() {
        var servicio = service(fixedClock(START), Duration.ofMinutes(5));
        var primera = servicio.reclamar(request(1L));
        var confirmada = servicio.confirmar(primera.solicitudId(), primera.tokenEjecucion(), RESULT_ID);

        var repeticion = servicio.reclamar(request(1L));

        assertThat(confirmada.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(repeticion).usingRecursiveComparison().isEqualTo(confirmada);
    }

    @Test
    void mismaClaveConParametrosDistintosProduceConflicto() {
        var servicio = service(fixedClock(START), Duration.ofMinutes(5));
        servicio.reclamar(request(1L));

        assertThatThrownBy(() -> servicio.reclamar(request(2L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void processingVigenteDevuelvePendienteYConservaElToken() {
        var servicio = service(fixedClock(START), Duration.ofMinutes(5));
        var primera = servicio.reclamar(request(1L));

        var pendiente = servicio.reclamar(request(1L));

        assertThat(pendiente.estado()).isEqualTo(MarketIdempotencyStatus.PROCESSING);
        assertThat(pendiente.tokenEjecucion()).isEqualTo(primera.tokenEjecucion());
    }

    @Test
    void processingVencidoPuedeSerReclamadoConNuevoToken() {
        var reloj = new MutableClock(START);
        var servicio = service(reloj, Duration.ofMinutes(5));
        var primera = servicio.reclamar(request(1L));
        reloj.advance(Duration.ofMinutes(6));

        var recuperada = servicio.reclamar(request(1L));

        assertThat(recuperada.estado()).isEqualTo(MarketIdempotencyStatus.PROCESSING);
        assertThat(recuperada.solicitudId()).isEqualTo(primera.solicitudId());
        assertThat(recuperada.tokenEjecucion()).isNotEqualTo(primera.tokenEjecucion());
    }

    @Test
    void confirmedRecuperaLaReferenciaDurable() {
        var servicio = service(fixedClock(START), Duration.ofMinutes(5));
        var primera = servicio.reclamar(request(1L));
        servicio.confirmar(primera.solicitudId(), primera.tokenEjecucion(), RESULT_ID);

        var recuperada = servicio.recuperar(USER_ID, "market-key");

        assertThat(recuperada).isPresent();
        assertThat(recuperada.orElseThrow().estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(recuperada.orElseThrow().referenciaResultado()).isEqualTo(RESULT_ID);
    }

    @Test
    void rejectedNoVuelveAEjecutarLaSolicitud() {
        var servicio = service(fixedClock(START), Duration.ofMinutes(5));
        var primera = servicio.reclamar(request(1L));
        var rechazada = servicio.rechazar(primera.solicitudId(), primera.tokenEjecucion());

        var repeticion = servicio.reclamar(request(1L));

        assertThat(rechazada.estado()).isEqualTo(MarketIdempotencyStatus.REJECTED);
        assertThat(repeticion.estado()).isEqualTo(MarketIdempotencyStatus.REJECTED);
        assertThat(repeticion.tokenEjecucion()).isNull();
    }

    @Test
    void respuestaPerdidaQuedaInciertaYEsRecuperableSinConfirmarla() {
        var servicio = service(fixedClock(START), Duration.ofMinutes(5));
        var primera = servicio.reclamar(request(1L));

        var incierta = servicio.marcarIncierta(primera.solicitudId(), primera.tokenEjecucion());
        var recuperada = servicio.recuperar(USER_ID, "market-key");

        assertThat(incierta.estado()).isEqualTo(MarketIdempotencyStatus.UNCERTAIN);
        assertThat(recuperada).isPresent();
        assertThat(recuperada.orElseThrow().estado()).isEqualTo(MarketIdempotencyStatus.UNCERTAIN);
        assertThat(recuperada.orElseThrow().referenciaResultado()).isNull();
    }

    @Test
    void tokenObsoletoNoPuedeConfirmarUnaSolicitudReclamada() {
        var reloj = new MutableClock(START);
        var servicio = service(reloj, Duration.ofMinutes(5));
        var primera = servicio.reclamar(request(1L));
        reloj.advance(Duration.ofMinutes(6));
        var recuperada = servicio.reclamar(request(1L));

        assertThatThrownBy(() -> servicio.confirmar(primera.solicitudId(), primera.tokenEjecucion(), RESULT_ID))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.STALE_REQUEST));
        assertThat(recuperada.tokenEjecucion()).isNotEqualTo(primera.tokenEjecucion());
    }

    @Test
    void resultadoInternoNoDebeConvertirseEnRespuestaPublica() {
        var resultado = new MarketIdempotencyResult(REQUEST_ID, USER_ID, "market-key", "PURCHASE",
                "hash-a", MarketIdempotencyStatus.PROCESSING,
                UUID.fromString("33333333-3333-3333-3333-333333333333"), null, START);

        assertThat(resultado.tokenEjecucion()).isNotNull();
        // El DTO es interno; T036 debe mapearlo sin tokenEjecucion ni UUID de cuenta.
        assertThat(resultado.usuarioId()).isEqualTo(USER_ID);
    }

    private static MarketIdempotencyRequest request(long quantity) {
        return new MarketIdempotencyRequest(USER_ID, "market-key", "PURCHASE", PLAYER_ID,
                quantity, QUOTE_ID, 1L);
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    protected static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant current) { this.current = current; }

        void advance(Duration duration) { current = current.plus(duration); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
