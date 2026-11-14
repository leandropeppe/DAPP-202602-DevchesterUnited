package com.devchesterunited.devchesterunitedv2.contract.market;

import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import com.devchesterunited.devchesterunitedv2.service.dto.market.*;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteVersion;
import java.util.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class QuotePublicationPortContractTest {

    private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void initialAndFutureQuotesUseTheSameVersionedSource() {
        InMemoryQuotePublicationPort port = new InMemoryQuotePublicationPort();

        QuoteVersion initial = port.publishInitialQuote(PLAYER_ID, new BigDecimal("1.00"), true);
        QuoteVersion future = port.publish(new QuotePublicationRequest(
                PLAYER_ID,
                new BigDecimal("1.25"),
                PUBLISHED_AT.plusSeconds(60),
                PUBLISHED_AT.plusSeconds(60),
                PUBLISHED_AT.plusSeconds(3600),
                "player-form-v1"));

        assertThat(initial.quoteId()).isEqualTo(future.quoteId());
        assertThat(initial.version()).isEqualTo(1);
        assertThat(future.version()).isEqualTo(2);
        assertThat(port.currentQuote(PLAYER_ID)).contains(future);
        assertThat(port.find(initial.quoteId(), initial.version())).contains(initial);
        assertThat(initial.pricePerToken()).isEqualByComparingTo("1.00");
        assertThat(initial.validUntil()).isNull();
        assertThat(initial.isValidAt(PUBLISHED_AT.plus(DurationHolder.ONE_DAY))).isTrue();
    }

    @Test
    void historicalVersionRemainsReadableButIsNotTheCurrentVersion() {
        InMemoryQuotePublicationPort port = new InMemoryQuotePublicationPort();

        QuoteVersion initial = port.publishInitialQuote(PLAYER_ID, new BigDecimal("1.00"), true);
        QuoteVersion replacement = port.publish(new QuotePublicationRequest(
                PLAYER_ID, new BigDecimal("1.25"), PUBLISHED_AT.plusSeconds(60),
                PUBLISHED_AT.plusSeconds(60), null, " valuation-v1 "));

        assertThat(port.currentQuote(PLAYER_ID)).contains(replacement);
        assertThat(port.find(initial.quoteId(), initial.version())).contains(initial);
        assertThat(port.find(initial.quoteId(), initial.version())).contains(initial);
    }

    @Test
    void externalValuationPublisherUsesOnlyThePublicationPortContract() {
        QuotePublicationPort tokenMarket = new InMemoryQuotePublicationPort();
        BigDecimal calculatedPrice = new BigDecimal("2.35");
        Instant publishedAt = PUBLISHED_AT.plusSeconds(60);

        QuoteVersion published = tokenMarket.publish(new QuotePublicationRequest(
                PLAYER_ID, calculatedPrice, publishedAt, publishedAt,
                publishedAt.plusSeconds(3600), "valuation-strategy-v3"));

        assertThat(published.pricePerToken()).isEqualByComparingTo(calculatedPrice);
        assertThat(published.strategyReference()).isEqualTo("valuation-strategy-v3");
        assertThat(published.validFrom()).isEqualTo(publishedAt);
        assertThat(published.validUntil()).isEqualTo(publishedAt.plusSeconds(3600));
        assertThat(tokenMarket.currentQuote(PLAYER_ID)).contains(published);
    }

    @Test
    void validityStatusesAndExpirationBoundaryNeverAcceptAnObsoleteQuote() {
        Instant validUntil = PUBLISHED_AT.plusSeconds(60);

        QuoteVersion valid = quote(QuoteValidityStatus.VALID, validUntil);
        QuoteVersion replaced = quote(QuoteValidityStatus.REPLACED, null);
        QuoteVersion invalidated = quote(QuoteValidityStatus.INVALIDATED, null);
        QuoteVersion expired = quote(QuoteValidityStatus.EXPIRED, validUntil.plusSeconds(60));

        assertThat(valid.isValidAt(PUBLISHED_AT.plusSeconds(59))).isTrue();
        assertThat(valid.isValidAt(validUntil)).isFalse();
        assertThat(replaced.isValidAt(PUBLISHED_AT.plusSeconds(1))).isFalse();
        assertThat(invalidated.isValidAt(PUBLISHED_AT.plusSeconds(1))).isFalse();
        assertThat(expired.isValidAt(PUBLISHED_AT.plusSeconds(1))).isFalse();
    }

    @Test
    void exactQuoteIdentityAndVersionAreRequiredForRecovery() {
        InMemoryQuotePublicationPort port = new InMemoryQuotePublicationPort();
        QuoteVersion initial = port.publishInitialQuote(PLAYER_ID, new BigDecimal("1.00"), true);

        assertThat(port.find(initial.quoteId(), initial.version())).contains(initial);
        assertThat(port.find(UUID.randomUUID(), initial.version())).isEmpty();
        assertThat(port.find(initial.quoteId(), initial.version() + 1)).isEmpty();
    }

    @Test
    void publicationRequestNormalizesPriceDatesAndStrategyReference() {
        QuotePublicationRequest request = new QuotePublicationRequest(
                PLAYER_ID, new BigDecimal("1.2"), PUBLISHED_AT,
                PUBLISHED_AT, PUBLISHED_AT.plusSeconds(60), "  valuation-v2  ");

        assertThat(request.pricePerToken()).isEqualByComparingTo("1.20");
        assertThat(request.publishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(request.validFrom()).isEqualTo(PUBLISHED_AT);
        assertThat(request.strategyReference()).isEqualTo("valuation-v2");
    }

    @Test
    void publishedVersionIsImmutableAndKeepsTheAcceptedPrice() {
        QuoteVersion version = new QuoteVersion(
                UUID.randomUUID(),
                1,
                PLAYER_ID,
                new BigDecimal("1.00"),
                PUBLISHED_AT,
                PUBLISHED_AT,
                null,
                QuoteValidityStatus.VALID,
                null);

        assertThat(QuoteVersion.class.isRecord()).isTrue();
        assertThat(version.pricePerToken()).isEqualByComparingTo("1.00");
        assertThat(version.strategyReference()).isNull();
        assertThat(version.isValidAt(PUBLISHED_AT)).isTrue();
    }

    @Test
    void rejectsInvalidQuoteData() {
        assertThatIllegalArgumentException().isThrownBy(() -> new QuoteVersion(
                UUID.randomUUID(), 0, PLAYER_ID, new BigDecimal("1.00"),
                PUBLISHED_AT, PUBLISHED_AT, null, QuoteValidityStatus.VALID, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new QuoteVersion(
                UUID.randomUUID(), 1, PLAYER_ID, new BigDecimal("0.00"),
                PUBLISHED_AT, PUBLISHED_AT, null, QuoteValidityStatus.VALID, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new QuoteVersion(
                UUID.randomUUID(), 1, PLAYER_ID, new BigDecimal("1.001"),
                PUBLISHED_AT, PUBLISHED_AT, null, QuoteValidityStatus.VALID, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new QuoteVersion(
                UUID.randomUUID(), 1, PLAYER_ID, new BigDecimal("1.00"),
                PUBLISHED_AT, PUBLISHED_AT, PUBLISHED_AT.minusSeconds(1),
                QuoteValidityStatus.VALID, null));
    }

    @Test
    void initialRequestUsesOneCreditAndNonExpiringValidity() {
        QuotePublicationRequest initial = QuotePublicationRequest.initial(PLAYER_ID, PUBLISHED_AT);

        assertThat(initial.playerId()).isEqualTo(PLAYER_ID);
        assertThat(initial.pricePerToken()).isEqualByComparingTo("1.00");
        assertThat(initial.validUntil()).isNull();
        assertThat(initial.strategyReference()).isNull();
    }

    @Test
    void rejectsInvalidInitialPublicationParameters() {
        InMemoryQuotePublicationPort port = new InMemoryQuotePublicationPort();

        assertThatIllegalArgumentException().isThrownBy(
                () -> port.publishInitialQuote(PLAYER_ID, new BigDecimal("1.01"), true));
        assertThatIllegalArgumentException().isThrownBy(
                () -> port.publishInitialQuote(PLAYER_ID, new BigDecimal("1.00"), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> port.publishInitialQuote(null, new BigDecimal("1.00"), true));
        assertThatIllegalArgumentException().isThrownBy(
                () -> port.publishInitialQuote(PLAYER_ID, null, true));
    }

    private static final class InMemoryQuotePublicationPort implements QuotePublicationPort {
        private final Map<UUID, List<QuoteVersion>> quotes = new ConcurrentHashMap<>();

        @Override
        public QuoteVersion publishInitialQuote(UUID playerId, BigDecimal pricePerToken, boolean nonExpiring) {
            QuotePublicationPort.validateInitialQuotePublication(playerId, pricePerToken, nonExpiring);
            List<QuoteVersion> versions = quotes.computeIfAbsent(playerId, ignored -> new ArrayList<>());
            if (!versions.isEmpty()) {
                return versions.get(0);
            }
            Instant now = PUBLISHED_AT;
            QuoteVersion initial = new QuoteVersion(
                    UUID.randomUUID(), 1, playerId, new BigDecimal("1.00"), now, now,
                    null, QuoteValidityStatus.VALID, null);
            versions.add(initial);
            return initial;
        }

        @Override
        public QuoteVersion publish(QuotePublicationRequest request) {
            List<QuoteVersion> versions = quotes.computeIfAbsent(request.playerId(), ignored -> new ArrayList<>());
            UUID quoteId = versions.isEmpty() ? UUID.randomUUID() : versions.get(0).quoteId();
            QuoteVersion version = new QuoteVersion(
                    quoteId,
                    versions.size() + 1,
                    request.playerId(),
                    request.pricePerToken(),
                    request.publishedAt(),
                    request.validFrom(),
                    request.validUntil(),
                    QuoteValidityStatus.VALID,
                    request.strategyReference());
            versions.add(version);
            return version;
        }

        @Override
        public QuoteInvalidationResult invalidate(QuoteInvalidationRequest request) {
            throw new UnsupportedOperationException("No forma parte de este contrato de publicacion.");
        }

        @Override
        public Optional<QuoteVersion> currentQuote(UUID playerId) {
            List<QuoteVersion> versions = quotes.get(playerId);
            return versions == null || versions.isEmpty()
                    ? Optional.empty()
                    : Optional.of(versions.get(versions.size() - 1));
        }

        @Override
        public Optional<QuoteVersion> find(UUID quoteId, long version) {
            return quotes.values().stream()
                    .flatMap(List::stream)
                    .filter(candidate -> candidate.quoteId().equals(quoteId)
                            && candidate.version() == version)
                    .findFirst();
        }
    }

    private static QuoteVersion quote(QuoteValidityStatus status, Instant validUntil) {
        return new QuoteVersion(UUID.randomUUID(), 1, PLAYER_ID, new BigDecimal("1.00"),
                PUBLISHED_AT, PUBLISHED_AT, validUntil, status, null);
    }

    private static final class DurationHolder {
        private static final java.time.Duration ONE_DAY = java.time.Duration.ofDays(1);
    }
}
