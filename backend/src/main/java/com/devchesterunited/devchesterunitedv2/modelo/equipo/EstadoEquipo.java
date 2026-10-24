package com.devchesterunited.devchesterunitedv2.modelo.equipo;

import java.time.Instant;
import java.util.Objects;

/** Estado de ciclo de vida y auditoria de un equipo. Es inmutable. */
public record EstadoEquipo(boolean activo, Instant creadoEn, Instant actualizadoEn) {

    public EstadoEquipo {
        Objects.requireNonNull(creadoEn, "La fecha de creacion es obligatoria.");
        Objects.requireNonNull(actualizadoEn,
                "La fecha de actualizacion es obligatoria.");
        if (actualizadoEn.isBefore(creadoEn)) {
            throw new IllegalArgumentException(
                    "La fecha de actualizacion no puede ser anterior a la creacion.");
        }
    }

    public static EstadoEquipo nuevo(Instant ahora) {
        return new EstadoEquipo(true, ahora, ahora);
    }

    public EstadoEquipo desactivar(Instant ahora) {
        return transicionar(false, ahora);
    }

    public EstadoEquipo reactivar(Instant ahora) {
        return transicionar(true, ahora);
    }

    private EstadoEquipo transicionar(boolean nuevoEstado, Instant ahora) {
        return new EstadoEquipo(nuevoEstado, creadoEn, ahora);
    }
}
