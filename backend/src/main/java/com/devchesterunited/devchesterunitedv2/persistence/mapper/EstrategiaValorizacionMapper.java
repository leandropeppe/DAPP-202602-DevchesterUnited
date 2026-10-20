package com.devchesterunited.devchesterunitedv2.persistence.mapper;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstrategiaValorizacionEntity;

public final class EstrategiaValorizacionMapper {
    private EstrategiaValorizacionMapper() { }

    public static EstrategiaValorizacionEntity aEntity(EstrategiaValorizacion estrategia, String nombre,
            String configuracionJson) {
        return new EstrategiaValorizacionEntity(estrategia.id(), estrategia.version(), nombre,
                estrategia.tipo().name(), true, estrategia.esProvisoria(), estrategia.valorBase(),
                estrategia.factorEscala(), configuracionJson);
    }
}
