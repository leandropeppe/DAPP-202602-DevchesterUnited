package com.devchesterunited.devchesterunitedv2.unit.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuotePublicationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


class TokenMarketValidationTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final Instant PUBLISHED_AT = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void acceptsPositivePriceWithAtMostTwoDecimals() {
        QuotePublicationRequest request = new QuotePublicationRequest(
                PLAYER_ID,
                new BigDecimal("1.2"),
                PUBLISHED_AT,
                PUBLISHED_AT,
                null,
                "  strategy-v1  ");

        assertThat(request.pricePerToken()).isEqualByComparingTo("1.20");
        assertThat(request.strategyReference()).isEqualTo("strategy-v1");
    }

    @Test
    void rejectsNonPositivePrice() {
        var zero = new BigDecimal("0.00");
        var negative = new BigDecimal("-0.01");
        assertThatThrownBy(() -> validRequest(zero))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validRequest(negative))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPriceWithMoreThanTwoDecimals() {
        var price = new BigDecimal("1.001");
        assertThatThrownBy(() -> validRequest(price))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRequiredQuoteData() {
        assertThatNullPointerException().isThrownBy(
                () -> new QuotePublicationRequest(
                        null, BigDecimal.ONE, PUBLISHED_AT, PUBLISHED_AT, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new QuotePublicationRequest(
                        PLAYER_ID, null, PUBLISHED_AT, PUBLISHED_AT, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new QuotePublicationRequest(
                        PLAYER_ID, BigDecimal.ONE, null, PUBLISHED_AT, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new QuotePublicationRequest(
                        PLAYER_ID, BigDecimal.ONE, PUBLISHED_AT, null, null, null));
    }

    @Test
    void rejectsInvalidValidityDates() {
        var afterPublication = PUBLISHED_AT.plusSeconds(1);
        var beforePublication = PUBLISHED_AT.minusSeconds(1);
        assertThatThrownBy(() -> new QuotePublicationRequest(
                PLAYER_ID,
                BigDecimal.ONE,
                afterPublication,
                PUBLISHED_AT,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuotePublicationRequest(
                PLAYER_ID,
                BigDecimal.ONE,
                PUBLISHED_AT,
                PUBLISHED_AT,
                beforePublication,
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesTypedMarketExceptionReason() {
        for (TokenMarketException.Reason reason : TokenMarketException.Reason.values()) {
            TokenMarketException exception = new TokenMarketException(reason, "error");

            assertThat(exception.reason()).isEqualTo(reason);
            assertThat(exception).hasMessage("error");
        }
    }

    @Test
    void rejectsMissingMarketExceptionReason() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TokenMarketException(null, "error"));
    }

    private static QuotePublicationRequest validRequest(BigDecimal price) {
        return new QuotePublicationRequest(
                PLAYER_ID, price, PUBLISHED_AT, PUBLISHED_AT, null, null);
    }
}
