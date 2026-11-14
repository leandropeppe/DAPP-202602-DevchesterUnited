package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteVersion;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Límite único para publicar y consultar cotizaciones versionadas.
 *
 * <p>Player Valuation es responsable de calcular externamente el precio y de
 * proporcionar la referencia de estrategia y la vigencia. Token Market solo
 * valida el request, persiste la versión inmutable, actualiza la cabecera
 * vigente y expone la versión exacta para su consumo. Este contrato no
 * calcula precios, no realiza scraping ni depende de estadísticas.</p>
 */
public interface QuotePublicationPort {

    /** Publica la cotización inicial no expirable del jugador elegible. */
    QuoteVersion publishInitialQuote(UUID playerId, BigDecimal pricePerToken, boolean nonExpiring);

    /**
     * Validates the invariants required for the initial quote publication.
     * Future persistent implementations must invoke this method before
     * creating the initial version.
     */
    static void validateInitialQuotePublication(
            UUID playerId, BigDecimal pricePerToken, boolean nonExpiring) {
        if (playerId == null) {
            throw new IllegalArgumentException("El jugador de la cotizacion inicial es obligatorio.");
        }
        if (pricePerToken == null) {
            throw new IllegalArgumentException("El precio de la cotizacion inicial es obligatorio.");
        }
        if (pricePerToken.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("La cotizacion inicial debe ser exactamente 1.00.");
        }
        if (!nonExpiring) {
            throw new IllegalArgumentException("La cotizacion inicial no puede expirar automaticamente.");
        }
    }

    /** Publica una nueva versión usando la misma fuente de cotizaciones. */
    QuoteVersion publish(QuotePublicationRequest request);

    /** Invalidates the exact current quote without mutating its historical version. */
    QuoteInvalidationResult invalidate(QuoteInvalidationRequest request);

    /** Obtiene la versión actualmente publicada para el jugador. */
    Optional<QuoteVersion> currentQuote(UUID playerId);

    /** Busca una versión concreta sin sustituirla por otra. */
    Optional<QuoteVersion> find(UUID quoteId, long version);
}
