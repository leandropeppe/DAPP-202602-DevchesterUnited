package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Límite único para publicar y consultar cotizaciones versionadas.
 *
 * <p>La implementación futura será compartida por la siembra inicial de
 * Token Market y por las publicaciones dinámicas de Player Valuation. Este
 * contrato no calcula precios ni depende de estadísticas.</p>
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

    /** Obtiene la versión actualmente publicada para el jugador. */
    Optional<QuoteVersion> currentQuote(UUID playerId);

    /** Busca una versión concreta sin sustituirla por otra. */
    Optional<QuoteVersion> find(UUID quoteId, long version);
}
