package com.devchesterunited.devchesterunitedv2.unit.market;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketBalanceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MarketBalanceServiceTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock private EstadoMercadoRepository estados;
    @Mock private CuentaCreditosMercadoRepository cuentas;

    @Test
    void consultaSaldoDelUsuarioAutenticadoSinEfectosDeEscritura() {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado("READY", 9L)));
        when(cuentas.findByUsuarioId(USER_ID)).thenReturn(Optional.of(cuenta("125.50")));

        var resultado = new MarketBalanceServiceImpl(estados, cuentas).consultar(USER_ID);

        assertThat(resultado.operativa()).isTrue();
        assertThat(resultado.epoch()).isEqualTo(9L);
        assertThat(resultado.saldo()).contains(new BigDecimal("125.50"));
        verify(estados).findSingletonForRead();
        verify(cuentas).findByUsuarioId(USER_ID);
        verifyNoMoreInteractions(estados, cuentas);
    }

    @Test
    void estadoNoReadyConservaElSaldoPeroMarcaLaConsultaComoNoOperativa() {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado("INITIALIZING", 4L)));
        when(cuentas.findByUsuarioId(USER_ID)).thenReturn(Optional.of(cuenta("10.00")));

        var resultado = new MarketBalanceServiceImpl(estados, cuentas).consultar(USER_ID);

        assertThat(resultado.operativa()).isFalse();
        assertThat(resultado.saldo()).contains(new BigDecimal("10.00"));
    }

    @Test
    void usuarioSinCuentaNoSeConvierteEnSaldoCeroNiCreaCuenta() {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(estado("READY", 1L)));
        when(cuentas.findByUsuarioId(USER_ID)).thenReturn(Optional.empty());

        var resultado = new MarketBalanceServiceImpl(estados, cuentas).consultar(USER_ID);

        assertThat(resultado.saldo()).isEmpty();
        verify(cuentas).findByUsuarioId(USER_ID);
        verifyNoMoreInteractions(cuentas);
    }

    private static EstadoMercadoEntity estado(String status, long epoch) {
        return new EstadoMercadoEntity((short) 1, status, epoch, null, null, null, NOW, NOW);
    }

    private static CuentaCreditosMercadoEntity cuenta(String saldo) {
        return new CuentaCreditosMercadoEntity(UUID.randomUUID(), USER_ID, null,
                new BigDecimal(saldo), NOW, NOW);
    }
}
