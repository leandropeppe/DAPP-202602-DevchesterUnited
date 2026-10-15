package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.impl.MarketInitializationBootstrap;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketInitializationBootstrapTest {
    private static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");
    private static final BigDecimal DEFAULT = new BigDecimal("1000000.00");
    private static final BigDecimal CONFIGURADA = new BigDecimal("250000.00");
    private static final BigDecimal INVALIDA = new BigDecimal("-1.00");

    @Mock EstadoMercadoRepository estados;
    @Mock ContraparteMercadoRepository contrapartes;
    @Mock CuentaCreditosMercadoRepository cuentas;

    private EstadoMercadoEntity estado;
    private ContraparteMercadoEntity superuser;

    @BeforeEach
    void preparar() {
        estado = new EstadoMercadoEntity(
                (short) 1, "UNINITIALIZED", 1L, null, null, null, AHORA, AHORA);
        superuser = new ContraparteMercadoEntity(
                UUID.randomUUID(), "SUPERUSER", "Token Market Superuser", AHORA);
    }

    @Test
    void creaSuperuserCuentaYReservaPredeterminada() {
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));
        when(contrapartes.save(any(ContraparteMercadoEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(contrapartes.findByTipoContraparteForUpdate("SUPERUSER"))
                .thenReturn(Optional.empty());
        when(cuentas.findByContraparteIdForUpdate(any())).thenReturn(Optional.empty());
        when(cuentas.save(any(CuentaCreditosMercadoEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = bootstrap(DEFAULT).bootstrap();

        assertThat(resultado.reservaInicialEfectiva()).isEqualByComparingTo(DEFAULT);
        assertThat(estado.getReservaInicialInicializada()).isEqualByComparingTo(DEFAULT);
        assertThat(estado.getEpoch()).isEqualTo(1L);
        verify(contrapartes).save(any(ContraparteMercadoEntity.class));
        var cuenta = ArgumentCaptor.forClass(CuentaCreditosMercadoEntity.class);
        verify(cuentas).save(cuenta.capture());
        assertThat(cuenta.getValue().getBalance()).isEqualByComparingTo(DEFAULT);
    }

    @Test
    void usaReservaConfiguradaAntesDeLaPrimeraInicializacion() {
        estado = new EstadoMercadoEntity(
                (short) 1, "UNINITIALIZED", 1L, CONFIGURADA, null, null, AHORA, AHORA);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));
        when(contrapartes.findByTipoContraparteForUpdate("SUPERUSER"))
                .thenReturn(Optional.of(superuser));
        when(cuentas.findByContraparteIdForUpdate(superuser.getId())).thenReturn(Optional.empty());
        when(cuentas.save(any(CuentaCreditosMercadoEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = bootstrap(INVALIDA).bootstrap();

        assertThat(resultado.reservaInicialEfectiva()).isEqualByComparingTo(CONFIGURADA);
        assertThat(estado.getReservaInicialInicializada()).isEqualByComparingTo(CONFIGURADA);
    }

    @Test
    void reutilizaCuentaYReservaYaInicializadasSinSobrescribirSaldo() {
        estado = new EstadoMercadoEntity(
                (short) 1, "UNINITIALIZED", 4L, CONFIGURADA, CONFIGURADA, null, AHORA, AHORA);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));
        var cuenta = new CuentaCreditosMercadoEntity(
                UUID.randomUUID(), null, superuser.getId(),
                new BigDecimal("123.45"), AHORA, AHORA);
        when(contrapartes.findByTipoContraparteForUpdate("SUPERUSER"))
                .thenReturn(Optional.of(superuser));
        when(cuentas.findByContraparteIdForUpdate(superuser.getId()))
                .thenReturn(Optional.of(cuenta));

        var resultado = bootstrap(INVALIDA).bootstrap();

        assertThat(cuenta.getBalance()).isEqualByComparingTo("123.45");
        assertThat(resultado.reservaInicialEfectiva()).isEqualByComparingTo(CONFIGURADA);
        verify(cuentas, never()).save(any(CuentaCreditosMercadoEntity.class));
        assertThat(estado.getEpoch()).isEqualTo(4L);
        assertThat(estado.getStatus()).isEqualTo("UNINITIALIZED");
    }

    @Test
    void reejecutaBootstrapSinDuplicarRegistros() {
        estado = new EstadoMercadoEntity(
                (short) 1, "UNINITIALIZED", 1L, DEFAULT, DEFAULT, null, AHORA, AHORA);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));
        var cuenta = new CuentaCreditosMercadoEntity(
                UUID.randomUUID(), null, superuser.getId(), DEFAULT, AHORA, AHORA);
        when(contrapartes.findByTipoContraparteForUpdate("SUPERUSER"))
                .thenReturn(Optional.of(superuser));
        when(cuentas.findByContraparteIdForUpdate(superuser.getId()))
                .thenReturn(Optional.of(cuenta));

        bootstrap(DEFAULT).bootstrap();
        bootstrap(DEFAULT).bootstrap();

        verify(contrapartes, never()).save(any(ContraparteMercadoEntity.class));
        verify(cuentas, never()).save(any(CuentaCreditosMercadoEntity.class));
    }

    @Test
    void rechazaCuentaExistenteSinReservaInicializadaSinCambiarEstado() {
        var cuenta = new CuentaCreditosMercadoEntity(
                UUID.randomUUID(), null, superuser.getId(), BigDecimal.ZERO, AHORA, AHORA);
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));
        when(contrapartes.findByTipoContraparteForUpdate("SUPERUSER"))
                .thenReturn(Optional.of(superuser));
        when(cuentas.findByContraparteIdForUpdate(superuser.getId()))
                .thenReturn(Optional.of(cuenta));

        assertThatThrownBy(() -> bootstrap(DEFAULT).bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin reserva inicializada");
        assertThat(estado.getReservaInicialInicializada()).isNull();
        assertThat(estado.getEpoch()).isEqualTo(1L);
        verify(cuentas, never()).save(any(CuentaCreditosMercadoEntity.class));
    }

    @Test
    void rechazaReservaInvalidaCuandoNoExisteReservaPersistida() {
        when(estados.findSingletonForUpdate()).thenReturn(Optional.of(estado));

        assertThatThrownBy(() -> bootstrap(INVALIDA).bootstrap())
                .isInstanceOf(IllegalArgumentException.class);

        verify(contrapartes, never()).findByTipoContraparteForUpdate(any());
        verify(contrapartes, never()).save(any(ContraparteMercadoEntity.class));
        verify(cuentas, never()).save(any(CuentaCreditosMercadoEntity.class));
    }

    private MarketInitializationBootstrap bootstrap(BigDecimal reserva) {
        return new MarketInitializationBootstrap(
                estados, contrapartes, cuentas,
                Clock.fixed(AHORA, ZoneOffset.UTC), reserva);
    }
}
