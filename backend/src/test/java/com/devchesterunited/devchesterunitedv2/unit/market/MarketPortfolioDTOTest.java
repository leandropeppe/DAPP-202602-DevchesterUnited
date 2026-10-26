package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPortfolioPositionResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPortfolioResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioPosition;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioQuote;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPortfolioDTOTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant PUBLISHED = Instant.parse("2026-10-18T12:00:00Z");
    private static final Instant VALID_FROM = Instant.parse("2026-10-18T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializaCurrentConSnapshotYValoresDerivados() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPortfolioPositionResponseDTO.fromDomain(currentPosition())));

        assertThat(json.get("player_id").asText()).isEqualTo(PLAYER_ID.toString());
        assertThat(json.get("quote_availability").asText()).isEqualTo("CURRENT");
        assertThat(json.get("quote_id").asText()).isEqualTo(QUOTE_ID.toString());
        assertThat(json.get("current_value").decimalValue())
                .isEqualByComparingTo(new BigDecimal("6.00000000"));
        assertThat(json.get("unrealized_pnl").decimalValue())
                .isEqualByComparingTo(new BigDecimal("1.50000000"));
    }

    @Test
    void serializaHistoricalSinValoracionActual() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPortfolioPositionResponseDTO.fromDomain(
                        position(MarketQuoteAvailability.HISTORICAL, true, false))));

        assertThat(json.get("quote_availability").asText()).isEqualTo("HISTORICAL");
        assertThat(json.get("quote_id").asText()).isEqualTo(QUOTE_ID.toString());
        assertThat(json.has("current_value")).isFalse();
        assertThat(json.has("unrealized_pnl")).isFalse();
    }

    @Test
    void serializaAbsentSinSnapshotNiValoresDerivados() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPortfolioPositionResponseDTO.fromDomain(
                        position(MarketQuoteAvailability.ABSENT, false, false))));

        assertThat(json.get("quote_availability").asText()).isEqualTo("ABSENT");
        assertThat(json.has("quote_id")).isFalse();
        assertThat(json.has("unit_price")).isFalse();
        assertThat(json.has("current_value")).isFalse();
        assertThat(json.has("unrealized_pnl")).isFalse();
    }

    @Test
    void omiteAgregadosOpcionalesCuandoElPortfolioEsIncompleto() throws Exception {
        var domain = new MarketPortfolioResult(
                List.of(position(MarketQuoteAvailability.ABSENT, false, false)),
                new BigDecimal("2.00000000"), Optional.empty(), Optional.empty(), false);

        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPortfolioResponseDTO.fromDomain(domain)));

        assertThat(json.get("open_positions")).hasSize(1);
        assertThat(json.get("realized_pnl").decimalValue())
                .isEqualByComparingTo(new BigDecimal("2.00000000"));
        assertThat(json.get("combined_result_complete").asBoolean()).isFalse();
        assertThat(json.has("unrealized_pnl")).isFalse();
        assertThat(json.has("combined_result")).isFalse();
        assertThat(json.get("open_players").asLong()).isEqualTo(1L);
        assertThat(json.get("total_tokens").asLong()).isEqualTo(3L);
        assertThat(json.get("total_pending_acquisition_cost").decimalValue())
                .isEqualByComparingTo("4.50000000");
        assertThat(json.get("total_current_value").isNull()).isTrue();
    }

    @Test
    void conservaPnlRealizadaEnPortfolioVacio() throws Exception {
        var domain = new MarketPortfolioResult(
                List.of(),
                new BigDecimal("3.00000000"),
                Optional.of(new BigDecimal("0.00000000")),
                Optional.of(new BigDecimal("3.00000000")),
                true);

        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPortfolioResponseDTO.fromDomain(domain)));

        assertThat(json.get("open_positions")).isEmpty();
        assertThat(json.get("realized_pnl").decimalValue())
                .isEqualByComparingTo(new BigDecimal("3.00000000"));
        assertThat(json.get("unrealized_pnl").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.00000000"));
        assertThat(json.get("combined_result").decimalValue())
                .isEqualByComparingTo(new BigDecimal("3.00000000"));
        assertThat(json.get("combined_result_complete").asBoolean()).isTrue();
        assertThat(json.get("open_players").asLong()).isZero();
        assertThat(json.get("total_tokens").asLong()).isZero();
        assertThat(json.get("total_pending_acquisition_cost").decimalValue())
                .isEqualByComparingTo("0");
        assertThat(json.get("total_current_value").decimalValue())
                .isEqualByComparingTo("0");
    }

    @Test
    void serializaDescripcionCanonicaDeLaPosicion() throws Exception {
        MarketPortfolioPosition position = new MarketPortfolioPosition(
                PLAYER_ID, 3L, new BigDecimal("4.50000000"), new BigDecimal("1.50000000"),
                MarketQuoteAvailability.CURRENT, Optional.empty(),
                Optional.of(new BigDecimal("6.00000000")),
                Optional.of(new BigDecimal("1.50000000")),
                "Kenny Tete", "Fulham", "Premier League");

        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPortfolioPositionResponseDTO.fromDomain(position)));

        assertThat(json.get("player_name").asText()).isEqualTo("Kenny Tete");
        assertThat(json.get("team").asText()).isEqualTo("Fulham");
        assertThat(json.get("league").asText()).isEqualTo("Premier League");
    }

    private MarketPortfolioPosition currentPosition() {
        return position(MarketQuoteAvailability.CURRENT, true, true);
    }

    private MarketPortfolioPosition position(MarketQuoteAvailability availability,
            boolean withQuote, boolean withValues) {
        return new MarketPortfolioPosition(
                PLAYER_ID,
                3L,
                new BigDecimal("4.50000000"),
                new BigDecimal("1.50000000"),
                availability,
                withQuote ? Optional.of(new MarketPortfolioQuote(
                        QUOTE_ID, 2L, new BigDecimal("2.00"), PUBLISHED, VALID_FROM, null))
                        : Optional.empty(),
                withValues ? Optional.of(new BigDecimal("6.00000000")) : Optional.empty(),
                withValues ? Optional.of(new BigDecimal("1.50000000")) : Optional.empty());
    }
}
