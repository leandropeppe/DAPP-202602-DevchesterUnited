package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPortfolioService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketTransactionService;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityErrorHandler;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketController;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionRecord;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MarketController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class MarketTransactionMockMvcTest {
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER_A = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PLAYER_B = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID TRANSACTION_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSACTION_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID QUOTE_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COUNTERPARTY = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID IDEMPOTENCY_A = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NEWER = Instant.parse("2026-10-18T12:00:00Z");
    private static final Instant OLDER = Instant.parse("2026-10-17T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean private MarketBalanceService balances;
    @MockitoBean private MarketAvailabilityService availability;
    @MockitoBean private MarketIdempotencyService idempotency;
    @MockitoBean private MarketPurchaseService purchases;
    @MockitoBean private MarketSaleService sales;
    @MockitoBean private MarketPortfolioService portfolios;
    @MockitoBean private MarketTransactionService transactions;

    @BeforeEach
    void limpiaInvocacionesDelServicioDeHistorial() {
        clearInvocations(transactions);
    }

    @Test
    void historialAutenticadoDevuelveSuccessPaginacionYSnapshotCompleto() throws Exception {
        when(transactions.consultar(eq(USER_A), any(MarketTransactionFilter.class)))
                .thenReturn(result(List.of(transaction(TRANSACTION_A, PLAYER_A, "SALE", NEWER,
                        new BigDecimal("12.50000000"), new BigDecimal("25.00000000"),
                        new BigDecimal("3.25000000"))), 1, 20, 1, 1));

        mvc.perform(get("/api/v1/market/transactions").with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.transactions[0].transaction_id").value(TRANSACTION_A.toString()))
                .andExpect(jsonPath("$.data.transactions[0].confirmed_at").value(NEWER.toString()))
                .andExpect(jsonPath("$.data.transactions[0].player_id").value(PLAYER_A.toString()))
                .andExpect(jsonPath("$.data.transactions[0].player_name_snapshot").value("Player snapshot"))
                .andExpect(jsonPath("$.data.transactions[0].operation_type").value("SALE"))
                .andExpect(jsonPath("$.data.transactions[0].quantity").value(2))
                .andExpect(jsonPath("$.data.transactions[0].unit_price").value(12.5))
                .andExpect(jsonPath("$.data.transactions[0].total_amount").value(25.0))
                .andExpect(jsonPath("$.data.transactions[0].counterparty_id").value(COUNTERPARTY.toString()))
                .andExpect(jsonPath("$.data.transactions[0].quote_id").value(QUOTE_A.toString()))
                .andExpect(jsonPath("$.data.transactions[0].quote_version").value(7))
                .andExpect(jsonPath("$.data.transactions[0].idempotency_request_id").value(IDEMPOTENCY_A.toString()))
                .andExpect(jsonPath("$.data.transactions[0].realized_pnl").value(3.25))
                .andExpect(jsonPath("$.data.pagination.current_page").value(1))
                .andExpect(jsonPath("$.data.pagination.page_size").value(20))
                .andExpect(jsonPath("$.data.pagination.total_pages").value(1))
                .andExpect(jsonPath("$.data.pagination.total_records").value(1));

        verify(transactions).consultar(eq(USER_A), any(MarketTransactionFilter.class));
    }

    @Test
    void convierteTodosLosFiltrosYUsaElUsuarioDelJwt() throws Exception {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        when(transactions.consultar(eq(USER_A), any(MarketTransactionFilter.class)))
                .thenReturn(result(List.of(), 2, 10, 3, 21));

        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("userId", USER_B.toString())
                        .queryParam("playerId", PLAYER_A.toString())
                        .queryParam("type", "SALE")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .queryParam("page", "2")
                        .queryParam("limit", "10")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.current_page").value(2))
                .andExpect(jsonPath("$.data.pagination.page_size").value(10))
                .andExpect(jsonPath("$.data.pagination.total_pages").value(3))
                .andExpect(jsonPath("$.data.pagination.total_records").value(21));

        var captor = org.mockito.ArgumentCaptor.forClass(MarketTransactionFilter.class);
        verify(transactions).consultar(eq(USER_A), captor.capture());
        MarketTransactionFilter filter = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(PLAYER_A, filter.jugadorId());
        org.junit.jupiter.api.Assertions.assertEquals("SALE", filter.tipoOperacion());
        org.junit.jupiter.api.Assertions.assertEquals(from, filter.desde());
        org.junit.jupiter.api.Assertions.assertEquals(to, filter.hasta());
        org.junit.jupiter.api.Assertions.assertEquals(2, filter.pagina());
        org.junit.jupiter.api.Assertions.assertEquals(10, filter.limite());
    }

    @Test
    void aceptaFiltroPurchase() throws Exception {
        when(transactions.consultar(eq(USER_A), any(MarketTransactionFilter.class)))
                .thenReturn(result(List.of(), 1, 20, 0, 0));

        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("type", "PURCHASE")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isOk());

        verify(transactions).consultar(eq(USER_A), any(MarketTransactionFilter.class));
    }

    @Test
    void mantieneElOrdenDescendenteRecibidoDelServicio() throws Exception {
        when(transactions.consultar(eq(USER_A), any(MarketTransactionFilter.class)))
                .thenReturn(result(List.of(
                        transaction(TRANSACTION_A, PLAYER_A, "PURCHASE", NEWER,
                                new BigDecimal("10.00000000"), new BigDecimal("10.00000000"), null),
                        transaction(TRANSACTION_B, PLAYER_B, "SALE", OLDER,
                                new BigDecimal("8.00000000"), new BigDecimal("16.00000000"), null)),
                        1, 20, 1, 2));

        mvc.perform(get("/api/v1/market/transactions").with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions[0].transaction_id").value(TRANSACTION_A.toString()))
                .andExpect(jsonPath("$.data.transactions[1].transaction_id").value(TRANSACTION_B.toString()))
                .andExpect(jsonPath("$.data.transactions[0].confirmed_at").value(NEWER.toString()))
                .andExpect(jsonPath("$.data.transactions[1].confirmed_at").value(OLDER.toString()))
                .andExpect(jsonPath("$.data.transactions[0].realized_pnl").doesNotExist());
        // El orden persistente ORDER BY confirmado_en DESC se cubre en la prueba de repositorio.
    }

    @Test
    void aislaLasConsultasDeDosUsuariosAutenticados() throws Exception {
        when(transactions.consultar(eq(USER_A), any(MarketTransactionFilter.class)))
                .thenReturn(result(List.of(transaction(TRANSACTION_A, PLAYER_A, "PURCHASE", NEWER,
                        new BigDecimal("10.00000000"), new BigDecimal("10.00000000"), null)), 1, 20, 1, 1));
        when(transactions.consultar(eq(USER_B), any(MarketTransactionFilter.class)))
                .thenReturn(result(List.of(transaction(TRANSACTION_B, PLAYER_B, "SALE", OLDER,
                        new BigDecimal("8.00000000"), new BigDecimal("8.00000000"), null)), 1, 20, 1, 1));

        mvc.perform(get("/api/v1/market/transactions").with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions[0].transaction_id").value(TRANSACTION_A.toString()));
        mvc.perform(get("/api/v1/market/transactions").with(jwtFor(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions[0].transaction_id").value(TRANSACTION_B.toString()));

        verify(transactions).consultar(eq(USER_A), any(MarketTransactionFilter.class));
        verify(transactions).consultar(eq(USER_B), any(MarketTransactionFilter.class));
    }

    @Test
    void rechazaConsultaSinAutenticacionSinInvocarElServicio() throws Exception {
        mvc.perform(get("/api/v1/market/transactions"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transactions);
    }

    @Test
    void rechazaJwtInvalidoYSujetoMalformado() throws Exception {
        mvc.perform(get("/api/v1/market/transactions")
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/market/transactions")
                        .with(jwt().jwt(token -> token.subject("usuario-invalido"))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transactions);
    }

    @Test
    void rechazaFiltrosInvalidosConBadRequest() throws Exception {
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("playerId", "no-es-uuid")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("type", "REFUND")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("from", "fecha-invalida")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("to", "fecha-invalida")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("page", "0")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("limit", "0")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("limit", "101")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("page", "no-es-numero")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("limit", "no-es-numero")
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactions);
    }

    @Test
    void rechazaRangoTemporalInvalido() throws Exception {
        mvc.perform(get("/api/v1/market/transactions")
                        .queryParam("from", NEWER.toString())
                        .queryParam("to", OLDER.toString())
                        .with(jwtFor(USER_A)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactions);
    }

    private static MarketTransactionResult result(List<MarketTransactionRecord> records,
            int page, int limit, long totalPages, long totalRecords) {
        return new MarketTransactionResult(records,
                new PaginacionResultado(page, limit, totalPages, totalRecords));
    }

    private static MarketTransactionRecord transaction(UUID id, UUID playerId, String type,
            Instant confirmedAt, BigDecimal unitPrice, BigDecimal totalAmount, BigDecimal pnl) {
        return new MarketTransactionRecord(
                new MarketTransactionRecord.Identity(id, USER_A, playerId),
                new MarketTransactionRecord.Snapshot("Player snapshot", type),
                new MarketTransactionRecord.Amounts(2L, unitPrice, totalAmount),
                new MarketTransactionRecord.Settlement(COUNTERPARTY, QUOTE_A, 7L),
                new MarketTransactionRecord.Audit(IDEMPOTENCY_A, pnl, confirmedAt));
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(token -> token.subject("usr_" + userId));
    }
}
