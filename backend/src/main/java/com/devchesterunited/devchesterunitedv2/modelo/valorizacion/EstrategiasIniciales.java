package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;

import java.util.List;

public record EstrategiasIniciales(List<EstrategiaValorizacion> habilitadas,
        String estrategiaVisibleId, int estrategiaVisibleVersion) {
    public EstrategiasIniciales {
        habilitadas = List.copyOf(habilitadas);
        if (habilitadas.isEmpty()) throw new IllegalArgumentException("Debe existir una estrategia habilitada.");
    }
}
