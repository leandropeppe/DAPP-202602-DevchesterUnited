package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;

/** Puerto de páginas estadísticas normalizadas, independiente del proveedor concreto. */
public interface ProveedorEstadisticasJugador {
    PaginaEstadisticasJugador obtenerPagina(CompeticionEstadistica competicion,
            FamiliaEstadistica familia, int pagina, int tamano);
}
