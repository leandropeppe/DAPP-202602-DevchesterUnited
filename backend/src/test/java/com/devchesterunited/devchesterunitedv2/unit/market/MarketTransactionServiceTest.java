package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketTransactionServiceTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID COUNTERPARTY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID QUOTE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID REQUEST_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID TRANSACTION_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-10-16T12:00:00Z");

    @Mock
    private TransaccionConfirmadaMercadoRepository transacciones;

    private MarketTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketTransactionServiceImpl(transacciones);
    }

    @Test
    void rechazaUsuarioNulo() {
        MarketTransactionFilter filter = filter(1, 20);

        assertThatThrownBy(() -> service.consultar(null, filter))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validaFiltrosDeOperacionFechasYPaginacion() {
        assertThatThrownBy(() -> new MarketTransactionFilter(null, "TRANSFER", null, null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketTransactionFilter(null, null,
                CONFIRMED_AT, CONFIRMED_AT, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketTransactionFilter(null, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketTransactionFilter(null, null, null, null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conviertePaginaUnoBasedAIndiceCeroYDelegaElUsuario() {
        MarketTransactionFilter filter = filter(2, 20);
        PageRequest expectedPage = PageRequest.of(1, 20);
        when(transacciones.buscar(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                eq(expectedPage))).thenReturn(new PageImpl<>(List.of(), expectedPage, 0));

        service.consultar(USER_ID, filter);

        verify(transacciones).buscar(USER_ID, null, null, null, null, expectedPage);
    }

    @Test
    void mapeaElSnapshotCompletoDeLaTransaccionConfirmada() {
        MarketTransactionFilter filter = filter(1, 20);
        TransaccionConfirmadaMercadoEntity entity = transaction();
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(transacciones.buscar(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                eq(pageRequest))).thenReturn(new PageImpl<>(List.of(entity), pageRequest, 1));

        MarketTransactionResult result = service.consultar(USER_ID, filter);

        assertThat(result.transacciones()).hasSize(1);
        var transaction = result.transacciones().get(0);
        assertThat(transaction.id()).isEqualTo(TRANSACTION_ID);
        assertThat(transaction.usuarioId()).isEqualTo(USER_ID);
        assertThat(transaction.jugadorId()).isEqualTo(PLAYER_ID);
        assertThat(transaction.nombreJugadorSnapshot()).isEqualTo("Historical Player");
        assertThat(transaction.tipoOperacion()).isEqualTo("SALE");
        assertThat(transaction.cantidad()).isEqualTo(2L);
        assertThat(transaction.precioUnitario()).isEqualByComparingTo("2.50");
        assertThat(transaction.montoTotal()).isEqualByComparingTo("5.00");
        assertThat(transaction.contraparteId()).isEqualTo(COUNTERPARTY_ID);
        assertThat(transaction.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(transaction.quoteVersion()).isEqualTo(1L);
        assertThat(transaction.solicitudIdempotenciaId()).isEqualTo(REQUEST_ID);
        assertThat(transaction.pnlRealizado()).isEqualByComparingTo("1.25000000");
        assertThat(transaction.confirmadoEn()).isEqualTo(CONFIRMED_AT);
    }

    @Test
    void devuelveMetadatosDePaginacion() {
        MarketTransactionFilter filter = filter(2, 2);
        PageRequest pageRequest = PageRequest.of(1, 2);
        when(transacciones.buscar(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                eq(pageRequest))).thenReturn(new PageImpl<>(List.of(transaction()), pageRequest, 5));

        MarketTransactionResult result = service.consultar(USER_ID, filter);

        assertThat(result.paginacion().paginaActual()).isEqualTo(2);
        assertThat(result.paginacion().tamanoPagina()).isEqualTo(2);
        assertThat(result.paginacion().totalRegistros()).isEqualTo(5);
        assertThat(result.paginacion().totalPaginas()).isEqualTo(3);
    }

    @Test
    void conservaPaginaFueraDeRangoYDevuelveListaVaciaConTotales() {
        MarketTransactionFilter filter = filter(4, 2);
        PageRequest pageRequest = PageRequest.of(3, 2);
        when(transacciones.buscar(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                eq(pageRequest))).thenReturn(new PageImpl<>(List.of(), pageRequest, 5));

        MarketTransactionResult result = service.consultar(USER_ID, filter);

        assertThat(result.transacciones()).isEmpty();
        assertThat(result.paginacion().paginaActual()).isEqualTo(4);
        assertThat(result.paginacion().totalRegistros()).isEqualTo(5);
        assertThat(result.paginacion().totalPaginas()).isEqualTo(3);
    }

    @Test
    void transmiteFiltrosSinPermitirCambiarElUsuario() {
        MarketTransactionFilter filter = new MarketTransactionFilter(PLAYER_ID, "SALE",
                CONFIRMED_AT, CONFIRMED_AT.plusSeconds(60), 1, 10);
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(transacciones.buscar(USER_ID, PLAYER_ID, "SALE", CONFIRMED_AT,
                CONFIRMED_AT.plusSeconds(60), pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        service.consultar(USER_ID, filter);

        verify(transacciones).buscar(USER_ID, PLAYER_ID, "SALE", CONFIRMED_AT,
                CONFIRMED_AT.plusSeconds(60), pageRequest);
    }

    private static MarketTransactionFilter filter(int pagina, int limite) {
        return new MarketTransactionFilter(null, null, null, null, pagina, limite);
    }

    private static TransaccionConfirmadaMercadoEntity transaction() {
        return new TransaccionConfirmadaMercadoEntity(TRANSACTION_ID, USER_ID, PLAYER_ID,
                "Historical Player", "SALE", 2L, new BigDecimal("2.50"),
                new BigDecimal("5.00"), COUNTERPARTY_ID, QUOTE_ID, 1L, REQUEST_ID,
                new BigDecimal("1.25000000"), CONFIRMED_AT);
    }
}
