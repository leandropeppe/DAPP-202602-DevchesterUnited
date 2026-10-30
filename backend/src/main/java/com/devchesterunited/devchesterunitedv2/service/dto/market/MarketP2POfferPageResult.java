package com.devchesterunited.devchesterunitedv2.service.dto.market;

import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;

import java.util.List;
import java.util.Objects;

/** Resultado paginado de la consulta publica de ofertas P2P. */
public record MarketP2POfferPageResult(List<MarketP2POfferView> offers,
        PaginacionResultado pagination) {
    public MarketP2POfferPageResult {
        offers = List.copyOf(Objects.requireNonNull(offers));
        Objects.requireNonNull(pagination);
    }
}
