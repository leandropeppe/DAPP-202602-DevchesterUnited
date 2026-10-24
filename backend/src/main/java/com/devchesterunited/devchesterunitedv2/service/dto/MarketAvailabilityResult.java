package com.devchesterunited.devchesterunitedv2.service.dto;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoMercado;
import java.util.List;

public record MarketAvailabilityResult(
        EstadoMercado estado,
        long epoch,
        boolean operativa,
        int pagina,
        int limite,
        long totalRegistros,
        long totalPaginas,
        List<MarketPlayerAvailability> jugadores) {

    public MarketAvailabilityResult {
        jugadores = List.copyOf(jugadores);
    }
}
