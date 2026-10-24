package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AsignacionCreditosInicialesRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AsignacionCreditosInicialesEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.service.impl.market.AsignacionCreditosInicialesService;
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
class AsignacionCreditosInicialesServiceTest {
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    @Mock UsuarioRepository usuarios;
    @Mock CuentaCreditosMercadoRepository cuentas;
    @Mock AsignacionCreditosInicialesRepository asignaciones;

    private AsignacionCreditosInicialesService servicio;

    @BeforeEach
    void configurar() {
        servicio = new AsignacionCreditosInicialesService(
                usuarios, cuentas, asignaciones, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(usuarios.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario()));
    }

    @Test
    void creaCuentaYAcreditaExactamenteMilCreditos() {
        when(asignaciones.findByUsuarioIdAndTipoAsignacionForUpdate(
                USUARIO_ID, "INITIAL_GRANT")).thenReturn(Optional.empty());
        when(cuentas.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.empty());
        when(cuentas.save(any(CuentaCreditosMercadoEntity.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.asignarParaUsuario(USUARIO_ID);

        ArgumentCaptor<CuentaCreditosMercadoEntity> cuenta =
                ArgumentCaptor.forClass(CuentaCreditosMercadoEntity.class);
        verify(cuentas).save(cuenta.capture());
        assertThat(cuenta.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(cuenta.getValue().getBalance()).isEqualByComparingTo("1000.00");

        ArgumentCaptor<AsignacionCreditosInicialesEntity> asignacion =
                ArgumentCaptor.forClass(AsignacionCreditosInicialesEntity.class);
        verify(asignaciones).save(asignacion.capture());
        assertThat(asignacion.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(asignacion.getValue().getTipoAsignacion()).isEqualTo("INITIAL_GRANT");
        assertThat(asignacion.getValue().getAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void conservaSaldoExistenteYSumaElCreditoInicial() {
        CuentaCreditosMercadoEntity cuenta = cuenta("250.00");
        when(asignaciones.findByUsuarioIdAndTipoAsignacionForUpdate(
                USUARIO_ID, "INITIAL_GRANT")).thenReturn(Optional.empty());
        when(cuentas.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(cuenta));

        servicio.asignarParaUsuario(USUARIO_ID);

        verify(cuentas).save(cuenta);
        assertThat(cuenta.getBalance()).isEqualByComparingTo("1250.00");
        verify(asignaciones).save(any(AsignacionCreditosInicialesEntity.class));
    }

    @Test
    void noModificaSaldoNiCreaAsignacionCuandoYaExisteElMarcador() {
        when(asignaciones.findByUsuarioIdAndTipoAsignacionForUpdate(
                USUARIO_ID, "INITIAL_GRANT"))
                .thenReturn(Optional.of(new AsignacionCreditosInicialesEntity(
                        UUID.randomUUID(), USUARIO_ID, UUID.randomUUID(), "INITIAL_GRANT",
                        new BigDecimal("1000.00"), AHORA)));

        servicio.asignarParaUsuario(USUARIO_ID);

        verify(cuentas, never()).findByUsuarioIdForUpdate(any());
        verify(cuentas, never()).save(any(CuentaCreditosMercadoEntity.class));
        verify(asignaciones, never()).save(any(AsignacionCreditosInicialesEntity.class));
    }

    @Test
    void usuarioInexistenteNoProduceEfectosDePersistencia() {
        when(usuarios.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarParaUsuario(USUARIO_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verify(asignaciones, never()).findByUsuarioIdAndTipoAsignacionForUpdate(any(), any());
        verify(cuentas, never()).findByUsuarioIdForUpdate(any());
        verify(cuentas, never()).save(any(CuentaCreditosMercadoEntity.class));
        verify(asignaciones, never()).save(any(AsignacionCreditosInicialesEntity.class));
    }

    private static UsuarioEntity usuario() {
        return new UsuarioEntity(USUARIO_ID, "usuario-t021", "usuario-t021@example.com",
                "hash", AHORA, AHORA);
    }

    private static CuentaCreditosMercadoEntity cuenta(String saldo) {
        return new CuentaCreditosMercadoEntity(
                UUID.randomUUID(), USUARIO_ID, null, new BigDecimal(saldo), AHORA, AHORA);
    }
}
