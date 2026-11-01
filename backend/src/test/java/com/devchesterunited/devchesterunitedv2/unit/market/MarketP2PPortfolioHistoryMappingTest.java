package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPortfolioPositionResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketTransactionResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T062: mapping contract for reserved/available quantities and history types. */
class MarketP2PPortfolioHistoryMappingTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID QUOTE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COUNTERPARTY = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    void mapeaTotalReservadoYDisponibleSinCantidadesNegativas() throws Exception {
        var dto = new MarketPortfolioPositionResponseDTO(
                "Player", "Team", "League", PLAYER, 10L,
                10L, 3L, 7L,
                new BigDecimal("20.00000000"), new BigDecimal("2.00000000"),
                "CURRENT", QUOTE, 1L, new BigDecimal("3.00"), NOW, NOW, null, null, null);

        var json = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules().readTree(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .findAndRegisterModules().writeValueAsString(dto));

        assertThat(json.get("total_quantity").asLong()).isEqualTo(10L);
        assertThat(json.get("reserved_quantity").asLong()).isEqualTo(3L);
        assertThat(json.get("available_quantity").asLong()).isEqualTo(7L);
        assertThat(json.get("available_quantity").asLong())
                .isEqualTo(json.get("total_quantity").asLong()
                        - json.get("reserved_quantity").asLong());
    }

    @Test
    void conservaLosTiposHistoricosNormalizados() {
        for (String type : new String[]{"PRIMARY_PURCHASE", "P2P_PURCHASE", "P2P_SALE"}) {
            MarketTransactionRecord transactionRecord = org.mockito.Mockito.mock(MarketTransactionRecord.class);
            org.mockito.Mockito.when(transactionRecord.id()).thenReturn(ID);
            org.mockito.Mockito.when(transactionRecord.confirmadoEn()).thenReturn(NOW);
            org.mockito.Mockito.when(transactionRecord.jugadorId()).thenReturn(PLAYER);
            org.mockito.Mockito.when(transactionRecord.nombreJugadorSnapshot()).thenReturn("Player snapshot");
            org.mockito.Mockito.when(transactionRecord.tipoOperacion()).thenReturn(type);
            org.mockito.Mockito.when(transactionRecord.cantidad()).thenReturn(2L);
            org.mockito.Mockito.when(transactionRecord.precioUnitario()).thenReturn(new BigDecimal("2.00"));
            org.mockito.Mockito.when(transactionRecord.montoTotal()).thenReturn(new BigDecimal("4.00"));
            org.mockito.Mockito.when(transactionRecord.contraparteId()).thenReturn(COUNTERPARTY);
            org.mockito.Mockito.when(transactionRecord.quoteId()).thenReturn(QUOTE);
            org.mockito.Mockito.when(transactionRecord.quoteVersion()).thenReturn(1L);
            org.mockito.Mockito.when(transactionRecord.solicitudIdempotenciaId()).thenReturn(ID);

            var dto = MarketTransactionResponseDTO.fromDomain(transactionRecord);
            assertThat(dto.tipoOperacion()).isEqualTo(type);
        }
    }
}
