package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketIdempotencyParameterHasherTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID QUOTE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void normalizaOperacionYParametrosAntesDeCalcularSha256() {
        var first = request(" purchase ", 1L, 1L);
        var equivalent = request("PURCHASE", 1L, 1L);

        assertThat(MarketIdempotencyParameterHasher.hash(first))
                .isEqualTo(MarketIdempotencyParameterHasher.hash(equivalent))
                .hasSize(64);
    }

    @Test
    void modificaElHashCuandoCambiaUnParametroFuncional() {
        assertThat(MarketIdempotencyParameterHasher.hash(request("PURCHASE", 1L, 1L)))
                .isNotEqualTo(MarketIdempotencyParameterHasher.hash(request("PURCHASE", 2L, 1L)));
    }

    @Test
    void rechazaParametrosNoPositivos() {
        assertThatThrownBy(() -> MarketIdempotencyParameterHasher.hash(
                request("PURCHASE", 0L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MarketIdempotencyRequest request(String operation, long quantity,
            long quoteVersion) {
        return new MarketIdempotencyRequest(USER_ID, "market-key", operation, PLAYER_ID,
                quantity, QUOTE_ID, quoteVersion);
    }
}
