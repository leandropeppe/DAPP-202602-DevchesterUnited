package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EmisionTokensMercadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ejecuta las operaciones de emisión atravesando el proxy transaccional. */
@Service
public class MarketTokenIssuanceTransactionalService {
    static final long INITIAL_ISSUANCE = 100L;

    private final JugadorRepository jugadores;
    private final EmisionTokensMercadoRepository emisiones;
    private final Clock clock;

    public MarketTokenIssuanceTransactionalService(JugadorRepository jugadores,
            EmisionTokensMercadoRepository emisiones, Clock clock) {
        this.jugadores = jugadores;
        this.emisiones = emisiones;
        this.clock = clock;
    }

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
                    "La emision existente del jugador es inconsistente con T022.");
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
