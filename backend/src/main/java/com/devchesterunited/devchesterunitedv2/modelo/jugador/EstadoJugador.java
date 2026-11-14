package com.devchesterunited.devchesterunitedv2.modelo.jugador;

import com.devchesterunited.devchesterunitedv2.modelo.exception.JugadorException;

import java.time.Instant;
import java.util.Objects;

/** Estado y ciclo de vida de un jugador. Es inmutable. */
public final class EstadoJugador {
    private final boolean activo;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    private EstadoJugador(boolean activo, Instant creadoEn, Instant actualizadoEn) {
        if (creadoEn == null || actualizadoEn == null || actualizadoEn.isBefore(creadoEn)) {
            throw new JugadorException("Las fechas del jugador no son coherentes.");
        }
        this.activo = activo;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    /** Crea el estado inicial de un jugador nuevo. */
    public static EstadoJugador nuevo(Instant ahora) {
        return new EstadoJugador(true, ahora, ahora);
    }

    /** Reconstruye el estado conservado en persistencia. */
    public static EstadoJugador desdePersistencia(boolean activo, Instant creadoEn,
            Instant actualizadoEn) {
        return new EstadoJugador(activo, creadoEn, actualizadoEn);
    }

    public EstadoJugador desactivar(Instant ahora) {
        return transicionar(false, ahora);
    }

    public EstadoJugador reactivar(Instant ahora) {
        return transicionar(true, ahora);
    }

    private EstadoJugador transicionar(boolean nuevoEstado, Instant ahora) {
        return new EstadoJugador(nuevoEstado, creadoEn, ahora);
    }

    public boolean isActivo() { return activo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) return true;
        if (!(objeto instanceof EstadoJugador otro)) return false;
        return activo == otro.activo
                && creadoEn.equals(otro.creadoEn)
                && actualizadoEn.equals(otro.actualizadoEn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activo, creadoEn, actualizadoEn);
    }
}
