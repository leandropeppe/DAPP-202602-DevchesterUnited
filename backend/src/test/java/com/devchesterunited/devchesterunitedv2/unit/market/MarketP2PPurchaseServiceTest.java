package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketP2PPurchaseServiceTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_OFFER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

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
