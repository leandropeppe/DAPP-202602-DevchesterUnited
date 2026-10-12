package com.devchesterunited.devchesterunitedv2.service.dto;

import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import java.util.List;

/** Resumen de una publicación completa, incluyendo jugadores omitidos individualmente. */
public record SincronizacionEstadisticasResultado(int adquiridos, int publicados, int omitidos,
        List<IdentidadEstadisticaNoResueltaException.Diagnostico> diagnosticos) {
    public SincronizacionEstadisticasResultado {
        diagnosticos = List.copyOf(diagnosticos);
    }
}
