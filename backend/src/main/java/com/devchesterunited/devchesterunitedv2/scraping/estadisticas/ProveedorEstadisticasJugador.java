package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;

import java.util.Collection;
import java.util.List;

/** Puerto de snapshots estadisticos completos, independiente del proveedor. */
public interface ProveedorEstadisticasJugador {
    /** Adquisicion completa de varias competiciones en una operacion de proveedor. */
    default List<SnapshotEstadisticasJugador> obtenerSnapshots(
            Collection<CompeticionEstadistica> competiciones) {
        if (competiciones == null) {
            throw new IllegalArgumentException("Las competiciones son obligatorias.");
        }
        return competiciones.stream().map(this::obtenerSnapshot).toList();
    }

    /** Adquisicion completa y normalizada de una competicion. */
    default SnapshotEstadisticasJugador obtenerSnapshot(CompeticionEstadistica competicion) {
        throw new UnsupportedOperationException("El proveedor debe entregar snapshots completos.");
    }
}
