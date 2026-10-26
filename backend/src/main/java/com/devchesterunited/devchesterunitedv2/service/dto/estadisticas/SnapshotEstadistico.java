package com.devchesterunited.devchesterunitedv2.service.dto.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;

import java.time.Instant;
import java.util.List;

public record SnapshotEstadistico(Instant capturadoEn, List<JugadorEstadistico> jugadores) {
    public SnapshotEstadistico { jugadores = List.copyOf(jugadores); }
}
