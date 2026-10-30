package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.Locale;
import java.util.UUID;

/** Filtros autenticados para la consulta publica de ofertas P2P. */
public record MarketP2POfferQuery(UUID usuarioId, UUID jugadorId, String estado,
        boolean soloMias, int pagina, int limite) {

    public MarketP2POfferQuery {
        if (usuarioId == null || pagina < 1 || limite < 1 || limite > 100) {
            throw new IllegalArgumentException("Los filtros de ofertas P2P no son validos.");
        }
        estado = normalizarEstado(estado);
    }

    private static String normalizarEstado(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("OPEN") && !normalized.equals("PARTIALLY_FILLED")
                && !normalized.equals("FILLED") && !normalized.equals("CANCELLED")
                && !normalized.equals("PURCHASABLE") && !normalized.equals("SUSPENDED")) {
            throw new IllegalArgumentException("El estado de la oferta no es valido.");
        }
        return normalized;
    }
}
