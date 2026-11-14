package com.devchesterunited.devchesterunitedv2.service.dto.estadisticas;

public record SincronizacionResultado(int equiposProcesados, int jugadoresProcesados,
        int equiposCreados, int equiposActualizados, int jugadoresCreados, int jugadoresActualizados) { }
