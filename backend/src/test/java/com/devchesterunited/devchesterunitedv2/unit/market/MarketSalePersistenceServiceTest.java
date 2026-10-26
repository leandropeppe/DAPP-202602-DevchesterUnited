package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EmisionTokensMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MovimientoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoId;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleSettlement;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketSalePersistenceServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSalePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MarketSalePersistenceServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID COUNTERPARTY_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID SUPERUSER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock private CuentaCreditosMercadoRepository cuentas;
    @Mock private EmisionTokensMercadoRepository emisiones;
    @Mock private TenenciaMercadoRepository tenencias;
    @Mock private TransaccionConfirmadaMercadoRepository transacciones;
    @Mock private MovimientoMercadoRepository movimientos;

    private MarketSalePersistenceService service;
    private CuentaCreditosMercadoEntity cuentaUsuario;
    private CuentaCreditosMercadoEntity cuentaSuperuser;
    private EmisionTokensMercadoEntity emision;
    private TenenciaMercadoEntity tenencia;

    @BeforeEach
    void setUp() {
        service = new MarketSalePersistenceServiceImpl(cuentas, emisiones, tenencias,
                transacciones, movimientos);
        cuentaUsuario = new CuentaCreditosMercadoEntity(USER_ACCOUNT_ID, USER_ID, null,
                new BigDecimal("100.00"), NOW, NOW);
        cuentaSuperuser = new CuentaCreditosMercadoEntity(SUPERUSER_ACCOUNT_ID, null,
                COUNTERPARTY_ID, new BigDecimal("100.00"), NOW, NOW);
        emision = new EmisionTokensMercadoEntity(PLAYER_ID, COUNTERPARTY_ID, 100L, 90L,
                NOW, NOW);
        UsuarioEntity usuario = new UsuarioEntity(USER_ID, "usuario", "usuario@example.com",
                "hash", NOW, NOW);
        JugadorEntity jugador = new JugadorEntity(PLAYER_ID, "Jugador", UUID.randomUUID(),
                "DEL", 10, "AR", true);
        tenencia = new TenenciaMercadoEntity(new TenenciaMercadoId(USER_ID, PLAYER_ID), usuario,
                jugador, 10L, new BigDecimal("15.00000000"), NOW);
    }

    @Test
    void ventaParcialReduceCostoProporcionalmenteYRegistraPnlPositiva() {
        givenRows();
        UUID transactionId = service.persistirVenta(settlement(4L, "2.00", "8.00"));

        assertThat(transactionId).isNotNull();
        assertThat(cuentaUsuario.getBalance()).isEqualByComparingTo("108.00");
        assertThat(cuentaSuperuser.getBalance()).isEqualByComparingTo("92.00");
        assertThat(emision.getCantidadInventario()).isEqualTo(94L);
        assertThat(tenencia.getQuantity()).isEqualTo(6L);
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("9.00000000");
        ArgumentCaptor<com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity> transaction =
                ArgumentCaptor.forClass(com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity.class);
        verify(transacciones).save(transaction.capture());
        assertThat(transaction.getValue().getTipoOperacion()).isEqualTo("SALE");
        assertThat(transaction.getValue().getPnlRealizado()).isEqualByComparingTo("2.00000000");
        verify(movimientos).saveAll(any());
    }

    @Test
    void ventaParcialRegistraPnlNegativaContraCostoAsignado() {
        givenRows();
        service.persistirVenta(settlement(4L, "1.00", "4.00"));

        assertThat(tenencia.getQuantity()).isEqualTo(6L);
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("9.00000000");
        ArgumentCaptor<com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity> transaction =
                ArgumentCaptor.forClass(com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity.class);
        verify(transacciones).save(transaction.capture());
        assertThat(transaction.getValue().getPnlRealizado()).isEqualByComparingTo("-2.00000000");
    }

    @Test
    void ventaFinalPoneEnCeroCantidadYCostoResidual() {
        givenRows();
        UUID transactionId = service.persistirVenta(settlement(10L, "2.00", "20.00"));

        assertThat(transactionId).isNotNull();
        assertThat(tenencia.getQuantity()).isZero();
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("0.00000000");
        assertThat(emision.getCantidadInventario()).isEqualTo(100L);
    }

    @Test
    void ventaConPnlNulaConservaLaTenenciaResidualSinRedondeoArbitrario() {
        tenencia = new TenenciaMercadoEntity(tenencia.getId(), tenencia.getUsuario(),
                tenencia.getJugador(), 3L, new BigDecimal("6.00000000"), NOW);
        givenAccountRows();
        givenEmissionRow();
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(tenencia));

        service.persistirVenta(settlement(1L, "2.00", "2.00"));

        assertThat(tenencia.getQuantity()).isEqualTo(2L);
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("4.00000000");
        ArgumentCaptor<com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity> transaction =
                ArgumentCaptor.forClass(com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity.class);
        verify(transacciones).save(transaction.capture());
        assertThat(transaction.getValue().getPnlRealizado()).isEqualByComparingTo("0.00000000");
    }

    @Test
    void ventaParcialConCostoProporcionalPeriodicoConservaElCosto() {
        tenencia = new TenenciaMercadoEntity(tenencia.getId(), tenencia.getUsuario(),
                tenencia.getJugador(), 3L, new BigDecimal("1.00000000"), NOW);
        givenAccountRows();
        givenEmissionRow();
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(tenencia));

        service.persistirVenta(settlement(1L, "1.00", "1.00"));

        assertThat(tenencia.getQuantity()).isEqualTo(2L);
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("0.66666667");
        ArgumentCaptor<com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity> transaction =
                ArgumentCaptor.forClass(com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity.class);
        verify(transacciones).save(transaction.capture());
        assertThat(transaction.getValue().getPnlRealizado()).isEqualByComparingTo("0.66666667");
        BigDecimal costoVendido = new BigDecimal("1.00")
                .subtract(transaction.getValue().getPnlRealizado());
        assertThat(costoVendido.add(tenencia.getCostoAdquisicion()))
                .isEqualByComparingTo("1.00000000");
    }

    @Test
    void ventasSucesivasConservanElCostoYLaPnlRealizada() {
        tenencia = new TenenciaMercadoEntity(tenencia.getId(), tenencia.getUsuario(),
                tenencia.getJugador(), 3L, new BigDecimal("1.00000000"), NOW);
        givenAccountRows();
        givenEmissionRow();
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(tenencia));

        service.persistirVenta(settlement(1L, "1.00", "1.00"));
        service.persistirVenta(settlement(1L, "1.00", "1.00"));
        service.persistirVenta(settlement(1L, "1.00", "1.00"));

        assertThat(tenencia.getQuantity()).isZero();
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("0.00000000");
        ArgumentCaptor<com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity> transactions =
                ArgumentCaptor.forClass(com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity.class);
        verify(transacciones, times(3)).save(transactions.capture());
        assertThat(transactions.getAllValues().get(0).getPnlRealizado())
                .isEqualByComparingTo("0.66666667");
        assertThat(transactions.getAllValues().get(1).getPnlRealizado())
                .isEqualByComparingTo("0.66666666");
        assertThat(transactions.getAllValues().get(2).getPnlRealizado())
                .isEqualByComparingTo("0.66666667");
        BigDecimal realizedPnl = transactions.getAllValues().stream()
                .map(com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity::getPnlRealizado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(realizedPnl).isEqualByComparingTo("2.00000000");
    }

    @Test
    void ventaParcialSeguidaDeCierreFinalDejaCostoCero() {
        tenencia = new TenenciaMercadoEntity(tenencia.getId(), tenencia.getUsuario(),
                tenencia.getJugador(), 3L, new BigDecimal("1.00000000"), NOW);
        givenAccountRows();
        givenEmissionRow();
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(tenencia));

        service.persistirVenta(settlement(1L, "1.00", "1.00"));
        service.persistirVenta(settlement(2L, "1.00", "2.00"));

        assertThat(tenencia.getQuantity()).isZero();
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("0.00000000");
    }

    @Test
    void tenenciaInsuficienteNoMutaNiRegistra() {
        emision.decrementarInventario(1L, NOW);
        givenRows();
        assertThatThrownBy(() -> service.persistirVenta(settlement(11L, "2.00", "22.00")))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.INSUFFICIENT_INVENTORY));

        assertThat(cuentaUsuario.getBalance()).isEqualByComparingTo("100.00");
        assertThat(cuentaSuperuser.getBalance()).isEqualByComparingTo("100.00");
        assertThat(emision.getCantidadInventario()).isEqualTo(89L);
        assertThat(tenencia.getQuantity()).isEqualTo(10L);
        verify(transacciones, never()).save(any());
        verify(movimientos, never()).saveAll(any());
    }

    @Test
    void reservaInsuficienteNoMutaNiRegistra() {
        cuentaSuperuser.debitar(new BigDecimal("99.00"), NOW);
        when(cuentas.findByUsuarioIdForUpdate(USER_ID)).thenReturn(Optional.of(cuentaUsuario));
        when(cuentas.findByContraparteIdForUpdate(COUNTERPARTY_ID))
                .thenReturn(Optional.of(cuentaSuperuser));

        assertThatThrownBy(() -> service.persistirVenta(settlement(2L, "2.00", "4.00")))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.INSUFFICIENT_BALANCE));

        assertThat(cuentaUsuario.getBalance()).isEqualByComparingTo("100.00");
        assertThat(emision.getCantidadInventario()).isEqualTo(90L);
        assertThat(tenencia.getQuantity()).isEqualTo(10L);
        verify(transacciones, never()).save(any());
    }

    @Test
    void totalQueNoCoincideConPrecioPorCantidadSeRechazaAntesDeMutar() {
        assertThatThrownBy(() -> service.persistirVenta(settlement(3L, "1.25", "3.74")))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.INVALID_INPUT));

        verify(cuentas, never()).findByUsuarioIdForUpdate(any());
        verify(emisiones, never()).findByJugadorIdForUpdate(any());
        verify(transacciones, never()).save(any());
    }

    @Test
    void importeConDosDecimalesSeCalculaSinRedondeoImplicitoAdicional() {
        givenRows();
        service.persistirVenta(settlement(3L, "0.29", "0.87"));

        assertThat(cuentaUsuario.getBalance()).isEqualByComparingTo("100.87");
        assertThat(cuentaSuperuser.getBalance()).isEqualByComparingTo("99.13");
    }

    @Test
    void conservaLaCantidadTotalEntreInventarioYSumaDeTenencias() {
        givenRows();
        long totalAntes = emision.getCantidadInventario() + tenencia.getQuantity();

        service.persistirVenta(settlement(4L, "2.00", "8.00"));

        long totalDespues = emision.getCantidadInventario() + tenencia.getQuantity();
        assertThat(totalDespues).isEqualTo(totalAntes).isEqualTo(100L);
    }

    private void givenRows() {
        givenAccountRows();
        givenEmissionRow();
        givenTenancyRow();
    }

    private void givenAccountRows() {
        when(cuentas.findByUsuarioIdForUpdate(USER_ID)).thenReturn(Optional.of(cuentaUsuario));
        when(cuentas.findByContraparteIdForUpdate(COUNTERPARTY_ID))
                .thenReturn(Optional.of(cuentaSuperuser));
    }

    private void givenEmissionRow() {
        when(emisiones.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(emision));
    }

    private void givenTenancyRow() {
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(tenencia));
    }

    private MarketSaleSettlement settlement(long quantity, String unitPrice, String total) {
        return new MarketSaleSettlement(REQUEST_ID, USER_ID, PLAYER_ID, "Jugador",
                COUNTERPARTY_ID, QUOTE_ID, 1L, quantity, new BigDecimal(unitPrice),
                new BigDecimal(total), NOW);
    }
}
