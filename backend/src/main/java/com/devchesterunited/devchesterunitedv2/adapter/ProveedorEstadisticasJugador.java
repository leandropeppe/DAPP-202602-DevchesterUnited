package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import java.util.Collection;
import java.util.List;

/**
 * Puerto de páginas estadísticas normalizadas, independiente del proveedor
 * concreto.
 *
 * La implementación traduce transporte, JSON y paginación externa antes de
 * devolver value objects internos. El consumidor solo puede depender de la
 * competencia, familia y metadata normalizadas de la página.
 */
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
        java.util.List<com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador> registros =
                new java.util.ArrayList<>();
        for (FamiliaEstadistica familia : FamiliaEstadistica.values()) {
            int pagina = 1;
            PaginaEstadisticasJugador respuesta;
            do {
                respuesta = obtenerPagina(competicion, familia, pagina, 50);
                registros.addAll(respuesta.registros());
                pagina++;
            } while (pagina <= respuesta.totalPaginas());
        }
        return new SnapshotEstadisticasJugador(competicion, registros);
    }

    /** Puente temporal; el Service no debe utilizar este metodo. */
    @Deprecated
    PaginaEstadisticasJugador obtenerPagina(CompeticionEstadistica competicion,
            FamiliaEstadistica familia, int pagina, int tamano);
}
