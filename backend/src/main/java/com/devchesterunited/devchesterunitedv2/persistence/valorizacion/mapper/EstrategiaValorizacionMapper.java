package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.mapper;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionEntity;

public final class EstrategiaValorizacionMapper {
    private EstrategiaValorizacionMapper() { }

    public static EstrategiaValorizacionEntity aEntity(EstrategiaValorizacion estrategia, String nombre,
            String configuracionJson) {
        return EstrategiaValorizacionEntity.builder().id(estrategia.id())
                .version(estrategia.version()).nombre(nombre).tipo(estrategia.tipo().name())
                .habilitadaParaCalculo(true).esProvisoria(estrategia.esProvisoria())
                .valorBase(estrategia.valorBase()).factorEscala(estrategia.factorEscala())
                .configuracionJson(configuracionJson).build();
    }
}
