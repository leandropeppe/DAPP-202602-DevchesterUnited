package com.devchesterunited.devchesterunitedv2.unit.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteVersion;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

class TokenMarketExceptionSerializationTest {
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final Instant VALID_FROM = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void conservaLaCotizacionActualAlSerializarLaExcepcion() throws Exception {
        QuoteVersion quote = quote();
        TokenMarketException original = new TokenMarketException(
                TokenMarketException.Reason.QUOTE_CHANGED, "changed", quote);

        TokenMarketException copy = roundTrip(original);

        assertThat(copy.reason()).isEqualTo(original.reason());
        assertThat(copy).hasMessage(original.getMessage());
        assertThat(copy.currentQuote()).isEqualTo(quote);
    }

    @Test
    void conservaLaAusenciaDeCotizacionAlSerializarLaExcepcion() throws Exception {
        TokenMarketException original = new TokenMarketException(
                TokenMarketException.Reason.QUOTE_UNAVAILABLE, "unavailable");

        TokenMarketException copy = roundTrip(original);

        assertThat(copy.reason()).isEqualTo(original.reason());
        assertThat(copy).hasMessage(original.getMessage());
        assertThat(copy.currentQuote()).isNull();
    }

    private static QuoteVersion quote() {
        return new QuoteVersion(QUOTE_ID, 2L, PLAYER_ID, new BigDecimal("3.00"),
                VALID_FROM, VALID_FROM, VALID_FROM.plusSeconds(3600),
                QuoteValidityStatus.VALID, "strategy-v1");
    }

    private static TokenMarketException roundTrip(TokenMarketException exception)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(exception);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (TokenMarketException) input.readObject();
        }
    }
}
