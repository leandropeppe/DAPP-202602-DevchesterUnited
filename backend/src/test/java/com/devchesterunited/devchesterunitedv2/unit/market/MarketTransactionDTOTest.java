package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketTransactionResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketTransactionsResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionRecord;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTransactionDTOTest {
    private static final UUID TRANSACTION_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID COUNTERPARTY_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID QUOTE_ID = UUID.fromString(
            "dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID REQUEST_ID = UUID.fromString(
            "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-10-18T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mapeaYSerializaElSnapshotCompletoDeLaTransaccion() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                MarketTransactionResponseDTO.fromDomain(transaction(
                        new BigDecimal("1.25000000")))));

        assertThat(json.get("transaction_id").asText()).isEqualTo(TRANSACTION_ID.toString());
        assertThat(json.get("confirmed_at").asText()).isEqualTo(CONFIRMED_AT.toString());
        assertThat(json.get("player_id").asText()).isEqualTo(PLAYER_ID.toString());
        assertThat(json.get("player_name_snapshot").asText()).isEqualTo("Historical Player");
        assertThat(json.get("operation_type").asText()).isEqualTo("SALE");
        assertThat(json.get("quantity").asLong()).isEqualTo(2L);
        assertThat(json.get("unit_price").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(json.get("total_amount").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(json.get("counterparty_id").asText()).isEqualTo(COUNTERPARTY_ID.toString());
        assertThat(json.get("quote_id").asText()).isEqualTo(QUOTE_ID.toString());
        assertThat(json.get("quote_version").asLong()).isEqualTo(1L);
        assertThat(json.get("idempotency_request_id").asText()).isEqualTo(REQUEST_ID.toString());
        assertThat(json.get("realized_pnl").decimalValue()).isEqualByComparingTo("1.25000000");
        assertThat(json.has("usuarioId")).isFalse();
        assertThat(json.has("user_id")).isFalse();
    }

    @Test
    void omitePnlRealizadaCuandoNoCorresponde() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                MarketTransactionResponseDTO.fromDomain(transaction(null))));

        assertThat(json.has("realized_pnl")).isFalse();
    }

    @Test
    void serializaTransaccionesYPaginacion() throws Exception {
        MarketTransactionResult result = new MarketTransactionResult(
                List.of(transaction(new BigDecimal("1.25000000"))),
                new PaginacionResultado(2, 10, 4, 31));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                MarketTransactionsResponseDTO.fromDomain(result)));

        assertThat(json.get("transactions")).hasSize(1);
        assertThat(json.get("pagination").get("current_page").asInt()).isEqualTo(2);
        assertThat(json.get("pagination").get("page_size").asInt()).isEqualTo(10);
        assertThat(json.get("pagination").get("total_pages").asLong()).isEqualTo(4L);
        assertThat(json.get("pagination").get("total_records").asLong()).isEqualTo(31L);
    }

    private static MarketTransactionRecord transaction(BigDecimal pnl) {
        return new MarketTransactionRecord(
                new MarketTransactionRecord.Identity(TRANSACTION_ID,
                        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), PLAYER_ID),
                new MarketTransactionRecord.Snapshot("Historical Player", "SALE"),
                new MarketTransactionRecord.Amounts(2L, new BigDecimal("2.50"),
                        new BigDecimal("5.00")),
                new MarketTransactionRecord.Settlement(COUNTERPARTY_ID, QUOTE_ID, 1L),
                new MarketTransactionRecord.Audit(REQUEST_ID, pnl, CONFIRMED_AT));
    }
}
