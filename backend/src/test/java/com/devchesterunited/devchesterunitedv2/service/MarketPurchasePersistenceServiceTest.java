package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EmisionTokensMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.MovimientoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.dto.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseSettlement;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.impl.MarketPurchasePersistenceServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class MarketPurchasePersistenceServiceTest {
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

    private MarketPurchasePersistenceServiceImpl service;
    private CuentaCreditosMercadoEntity cuentaUsuario;
    private CuentaCreditosMercadoEntity cuentaSuperuser;
    private EmisionTokensMercadoEntity emision;
    private TenenciaMercadoEntity tenencia;

    @BeforeEach
    void setUp() {
        service = new MarketPurchasePersistenceServiceImpl(cuentas, emisiones, tenencias,
                transacciones, movimientos);
        cuentaUsuario = new CuentaCreditosMercadoEntity(USER_ACCOUNT_ID, USER_ID, null,
                new BigDecimal("100.00"), NOW, NOW);
        cuentaSuperuser = new CuentaCreditosMercadoEntity(SUPERUSER_ACCOUNT_ID, null,
                COUNTERPARTY_ID, new BigDecimal("0.00"), NOW, NOW);
        emision = new EmisionTokensMercadoEntity(PLAYER_ID, COUNTERPARTY_ID, 100L, 100L,
                NOW, NOW);
        UsuarioEntity usuario = new UsuarioEntity(USER_ID, "usuario", "usuario@example.com",
                "hash", NOW, NOW);
        JugadorEntity jugador = new JugadorEntity(PLAYER_ID, "Jugador", UUID.randomUUID(),
                "DEL", 10, "AR", true);
        tenencia = new TenenciaMercadoEntity(new TenenciaMercadoId(USER_ID, PLAYER_ID), usuario,
                jugador, 0L, BigDecimal.ZERO.setScale(8), NOW);
    }

    @Test
    void persisteCompraAtomicaYActualizaLosRecursos() {
        givenAccountAndInventoryRows();
        givenTenancyRow();

        UUID transactionId = service.persistirCompra(settlement(3L, "1.25", "3.75"));

        assertThat(transactionId).isNotNull();
        assertThat(cuentaUsuario.getBalance()).isEqualByComparingTo("96.25");
        assertThat(cuentaSuperuser.getBalance()).isEqualByComparingTo("3.75");
        assertThat(emision.getCantidadInventario()).isEqualTo(97L);
        assertThat(tenencia.getQuantity()).isEqualTo(3L);
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("3.75000000");
        verify(transacciones).save(any());
        verify(movimientos).saveAll(any());
    }

    @Test
    void acumulaElCostoTotalParaConservarElPromedioPonderado() {
        givenAccountAndInventoryRows();
        givenTenancyRow();
        tenencia.aplicarCompra(2L, new BigDecimal("2.50"), NOW);

        service.persistirCompra(settlement(3L, "1.25", "3.75"));

        assertThat(tenencia.getQuantity()).isEqualTo(5L);
        assertThat(tenencia.getCostoAdquisicion()).isEqualByComparingTo("6.25000000");
        assertThat(tenencia.getCostoAdquisicion()
                .divide(BigDecimal.valueOf(tenencia.getQuantity())))
                .isEqualByComparingTo("1.25");
    }

    @Test
    void saldoInsuficienteNoMutaNiRegistra() {
        givenAccountAndInventoryRows();

        assertThatThrownBy(() -> service.persistirCompra(settlement(3L, "40.00", "120.00")))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INSUFFICIENT_BALANCE));

        assertThat(cuentaUsuario.getBalance()).isEqualByComparingTo("100.00");
        assertThat(emision.getCantidadInventario()).isEqualTo(100L);
        assertThat(tenencia.getQuantity()).isZero();
        verify(transacciones, never()).save(any());
        verify(movimientos, never()).saveAll(any());
    }

    @Test
    void inventarioInsuficienteNoMutaNiRegistra() {
        givenAccountAndInventoryRows();
        emision.decrementarInventario(99L, NOW);

        assertThatThrownBy(() -> service.persistirCompra(settlement(2L, "1.25", "2.50")))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INSUFFICIENT_INVENTORY));

        assertThat(cuentaUsuario.getBalance()).isEqualByComparingTo("100.00");
        assertThat(tenencia.getQuantity()).isZero();
        verify(transacciones, never()).save(any());
    }

    @Test
    void importeQueNoCoincideConLaCotizacionSeRechazaAntesDeBloquearRecursos() {
        assertThatThrownBy(() -> service.persistirCompra(settlement(3L, "1.25", "3.74")))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INVALID_INPUT));

        verify(cuentas, never()).findByUsuarioIdForUpdate(any());
        verify(emisiones, never()).findByJugadorIdForUpdate(any());
    }

    private void givenAccountAndInventoryRows() {
        when(cuentas.findByUsuarioIdForUpdate(USER_ID)).thenReturn(Optional.of(cuentaUsuario));
        when(cuentas.findByContraparteIdForUpdate(COUNTERPARTY_ID))
                .thenReturn(Optional.of(cuentaSuperuser));
        when(emisiones.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(emision));
    }

    private void givenTenancyRow() {
        when(tenencias.ensureCanonicalZero(USER_ID, PLAYER_ID)).thenReturn(0);
        when(tenencias.findByUsuarioIdAndJugadorIdForUpdate(USER_ID, PLAYER_ID))
                .thenReturn(Optional.of(tenencia));
    }

    private MarketPurchaseSettlement settlement(long quantity, String unitPrice, String total) {
        return new MarketPurchaseSettlement(REQUEST_ID, USER_ID, PLAYER_ID, "Jugador",
                COUNTERPARTY_ID, QUOTE_ID, 1L, quantity, new BigDecimal(unitPrice),
                new BigDecimal(total), NOW);
    }
}
