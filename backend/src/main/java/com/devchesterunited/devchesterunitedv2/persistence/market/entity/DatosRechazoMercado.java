package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

public record DatosRechazoMercado(
        String hashClaveIdempotencia,
        String codigoMotivo,
        String hashParametros) {
}
