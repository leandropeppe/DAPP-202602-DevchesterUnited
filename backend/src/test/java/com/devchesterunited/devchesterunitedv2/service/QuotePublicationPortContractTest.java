package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.devchesterunited.devchesterunitedv2.service.dto.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.QuotePublicationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

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

    private static final class DurationHolder {
        private static final java.time.Duration ONE_DAY = java.time.Duration.ofDays(1);
    }
}
