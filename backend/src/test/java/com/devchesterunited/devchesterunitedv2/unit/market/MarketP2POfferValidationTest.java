package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2POfferRequestDTO;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketP2POfferValidationTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void hashDeOfertaNoRequiereCotizacionYNoAlteraHashPrimario() {
        var offer = new MarketIdempotencyRequest(USER_ID, "offer-key", "P2P_OFFER",
                PLAYER_ID, 3L, null, null);
        var purchase = new MarketIdempotencyRequest(USER_ID, "purchase-key", "PURCHASE",
                PLAYER_ID, 3L, UUID.fromString("22222222-2222-2222-2222-222222222222"), 1L);

        assertThat(MarketIdempotencyParameterHasher.hash(offer)).hasSize(64);
        assertThat(MarketIdempotencyParameterHasher.hash(purchase)).hasSize(64);
        assertThat(MarketIdempotencyParameterHasher.hash(offer))
                .isNotEqualTo(MarketIdempotencyParameterHasher.hash(purchase));
    }

    @Test
    void precioManualSeRechazaExplicitamente() {
        var request = new MarketP2POfferRequestDTO();
        request.registrarCampoDesconocido("price", null);

        assertThatThrownBy(request::validarCamposAdicionales)
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.MANUAL_PRICE_NOT_ALLOWED));
    }

    @Test
    void identidadSeleccionableDesdeBodySeRechaza() {
        var request = new MarketP2POfferRequestDTO();
        request.registrarCampoDesconocido("user_id", null);

        assertThatThrownBy(request::validarCamposAdicionales)
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INVALID_INPUT));
    }
}
