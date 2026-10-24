package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.EmisionTokensMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emite el suministro inicial de un jugador sin volver a emitirlo al reactivarlo. */
@Service
public class MarketTokenIssuanceService {
    static final long INITIAL_ISSUANCE = 100L;

    private final JugadorRepository jugadores;
    private final EmisionTokensMercadoRepository emisiones;
    private final Clock clock;

    public MarketTokenIssuanceService(JugadorRepository jugadores,
            EmisionTokensMercadoRepository emisiones, Clock clock) {
        this.jugadores = jugadores;
        this.emisiones = emisiones;
        this.clock = clock;
    }

    /** Emite todos los jugadores activos en un orden estable durante el bootstrap. */
    @Transactional
    public void emitirJugadoresActivos(UUID contraparteId) {
        emitirJugadoresActivosYObtener(contraparteId);
    }

    /**
     * Emite, valida y devuelve los jugadores que quedaron elegibles para la
     * cotización inicial dentro de la misma transacción del bootstrap.
     */
    @Transactional
    public List<UUID> emitirJugadoresActivosYObtener(UUID contraparteId) {
        requireCounterparty(contraparteId);
        List<UUID> elegibles = new ArrayList<>();
        for (JugadorEntity jugador : jugadores.findAllByActivoTrueOrderByIdAsc()) {
            if (emitirJugadorBloqueado(jugador.getId(), contraparteId)) {
                elegibles.add(jugador.getId());
            }
        }
        return List.copyOf(elegibles);
    }

    /**
     * Flujo utilizado por el primer evento de activación. El jugador se bloquea
     * antes de leer su estado, por lo que una desactivación concurrente no puede
     * producir una emisión contra un estado que ya dejó de ser activo.
     */
    @Transactional
    public void emitirEnPrimeraActivacion(UUID jugadorId, UUID contraparteId) {
        requirePlayer(jugadorId);
        requireCounterparty(contraparteId);
        emitirJugadorBloqueado(jugadorId, contraparteId);
    }

    private boolean emitirJugadorBloqueado(UUID jugadorId, UUID contraparteId) {
        JugadorEntity jugador = jugadores.findByIdForUpdate(jugadorId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el jugador."));
        if (!jugador.isActivo()) {
            return false;
        }

        var existente = emisiones.findByJugadorIdForUpdate(jugadorId);
        if (existente.isPresent()) {
            validarEmisionExistente(existente.get(), contraparteId);
            return true;
        }

        Instant momento = clock.instant();
        emisiones.save(new EmisionTokensMercadoEntity(
                jugadorId, contraparteId, INITIAL_ISSUANCE, INITIAL_ISSUANCE,
                momento, momento));
        return true;
    }

    private static void validarEmisionExistente(EmisionTokensMercadoEntity emision,
            UUID contraparteId) {
        if (!contraparteId.equals(emision.getContraparteId())
                || emision.getCantidadEmitida() != INITIAL_ISSUANCE
                || emision.getCantidadInventario() < 0
                || emision.getCantidadInventario() > INITIAL_ISSUANCE) {
            throw new IllegalStateException(
                    "La emisión existente del jugador es inconsistente con T022.");
        }
    }

    private static void requirePlayer(UUID jugadorId) {
        if (jugadorId == null) {
            throw new IllegalArgumentException("El jugador es obligatorio.");
        }
    }

    private static void requireCounterparty(UUID contraparteId) {
        if (contraparteId == null) {
            throw new IllegalArgumentException("La contraparte es obligatoria.");
        }
    }
}
