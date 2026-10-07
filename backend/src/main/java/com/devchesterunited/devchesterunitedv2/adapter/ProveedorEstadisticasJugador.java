package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;

/**
 * Puerto de páginas estadísticas normalizadas, independiente del proveedor
 * concreto.
 *
 * La implementación traduce transporte, JSON y paginación externa antes de
 * devolver value objects internos. El consumidor solo puede depender de la
 * competencia, familia y metadata normalizadas de la página.
 */
public interface ProveedorEstadisticasJugador {
    PaginaEstadisticasJugador obtenerPagina(CompeticionEstadistica competicion,
            FamiliaEstadistica familia, int pagina, int tamano);
}
