package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AsignacionCreditosInicialesRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AsignacionCreditosInicialesEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Asigna una vez los créditos iniciales de un usuario. */
@Service
public class AsignacionCreditosInicialesService {
    static final String TIPO_ASIGNACION_INICIAL = "INITIAL_GRANT";
    static final BigDecimal IMPORTE_INICIAL = new BigDecimal("1000.00");

    private final UsuarioRepository usuarios;
    private final CuentaCreditosMercadoRepository cuentas;
    private final AsignacionCreditosInicialesRepository asignaciones;
    private final Clock clock;

    public AsignacionCreditosInicialesService(
            UsuarioRepository usuarios,
            CuentaCreditosMercadoRepository cuentas,
            AsignacionCreditosInicialesRepository asignaciones,
            Clock clock) {
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.asignaciones = asignaciones;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asignarParaUsuario(UUID usuarioId) {
        Instant momento = clock.instant();
        usuarios.findByIdForUpdate(usuarioId).orElseThrow(() ->
                new IllegalArgumentException("No existe el usuario a asignar."));

        if (asignaciones.findByUsuarioIdAndTipoAsignacionForUpdate(
                usuarioId, TIPO_ASIGNACION_INICIAL).isPresent()) {
            return;
        }

        CuentaCreditosMercadoEntity cuenta = cuentas.findByUsuarioIdForUpdate(usuarioId)
                .orElseGet(() -> new CuentaCreditosMercadoEntity(
                        UUID.randomUUID(), usuarioId, null, BigDecimal.ZERO, momento, momento));
        cuenta.acreditar(IMPORTE_INICIAL, momento);
        cuentas.save(cuenta);

        asignaciones.save(new AsignacionCreditosInicialesEntity(
                UUID.randomUUID(), usuarioId, cuenta.getId(), TIPO_ASIGNACION_INICIAL,
                IMPORTE_INICIAL, momento));
    }
}
