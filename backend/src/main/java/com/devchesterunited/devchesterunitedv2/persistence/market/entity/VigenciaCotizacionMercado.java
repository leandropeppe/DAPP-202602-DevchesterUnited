package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import java.time.Instant;

public record VigenciaCotizacionMercado(
        Instant publicadoEn,
        Instant validoDesde,
        Instant validoHasta) {
}
