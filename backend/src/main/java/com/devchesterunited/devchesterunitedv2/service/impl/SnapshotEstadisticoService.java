package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.mapper.EstadisticasJugadorMapper;
import com.devchesterunited.devchesterunitedv2.service.dto.SnapshotEstadistico;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SnapshotEstadisticoService {

    private final EstadisticasJugadorRepository estadisticasRepository;
    private final JugadorRepository jugadorRepository;
    private final Clock clock;

    public SnapshotEstadisticoService(
            EstadisticasJugadorRepository estadisticasRepository,
            JugadorRepository jugadorRepository,
            Clock clock) {
        this.estadisticasRepository = estadisticasRepository;
        this.jugadorRepository = jugadorRepository;
        this.clock = clock;
    }

    public SnapshotEstadistico capturar() {
        Map<UUID, String> posicionesPorJugador = new HashMap<>();
        jugadorRepository.findAll().forEach(jugador ->
                posicionesPorJugador.put(jugador.getId(), jugador.getPosicion()));

        return new SnapshotEstadistico(
                Instant.now(clock),
                estadisticasRepository.findAll().stream()
                        .map(entity -> new JugadorEstadistico(
                                entity.getJugadorId(),
                                posicionesPorJugador.get(entity.getJugadorId()),
                                EstadisticasJugadorMapper.aModelo(entity)))
                        .toList()
        );
    }
}
