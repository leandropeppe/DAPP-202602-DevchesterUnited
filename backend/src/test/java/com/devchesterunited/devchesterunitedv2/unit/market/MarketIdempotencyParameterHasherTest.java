package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void conservaCanonicalizacionHistoricaDeSale() {
        assertThat(MarketIdempotencyParameterHasher.hash(request(" SALE ", 1L, 1L)))
                .isEqualTo("d722aba40b7df448c17987b79ef7ec92e8ab61cf4c6f6920666bb180a85ece8a");
    }

    @Test
    void conservaCanonicalizacionHistoricaDeOfertaP2P() {
        assertThat(MarketIdempotencyParameterHasher.hash(request(" p2p_offer ", 1L, 7L)))
                .isEqualTo("9ee673c0263de783eb2f2224db0880a3b861863651996140033cdf021ff9a8bb");
        assertThat(MarketIdempotencyParameterHasher.hash(request("P2P_OFFER", 1L, 1L)))
                .isEqualTo(MarketIdempotencyParameterHasher.hash(request("P2P_OFFER", 1L, 7L)));
    }

    private static MarketIdempotencyRequest request(String operation, long quantity,
            long quoteVersion) {
        return new MarketIdempotencyRequest(USER_ID, "market-key", operation, PLAYER_ID,
                quantity, QUOTE_ID, quoteVersion);
    }
}
