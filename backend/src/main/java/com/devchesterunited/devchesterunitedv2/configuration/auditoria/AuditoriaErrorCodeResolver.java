package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import com.devchesterunited.devchesterunitedv2.exception.CotizacionNoEncontradaException;
import com.devchesterunited.devchesterunitedv2.exception.JugadorNoEncontradoException;
import com.devchesterunited.devchesterunitedv2.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.exception.SincronizacionEstadisticasEnCursoException;
import com.devchesterunited.devchesterunitedv2.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataException;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;

/** Mapea tipos de excepción a un vocabulario de auditoría reducido, estable e independiente del mensaje. */
public final class AuditoriaErrorCodeResolver {
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR";
    public static final String AUTHORIZATION_ERROR = "AUTHORIZATION_ERROR";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String CONCURRENCY_ERROR = "CONCURRENCY_ERROR";
    public static final String EXTERNAL_PROVIDER_ERROR = "EXTERNAL_PROVIDER_ERROR";
    public static final String PERSISTENCE_ERROR = "PERSISTENCE_ERROR";
    public static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";

    private AuditoriaErrorCodeResolver() {
        // Clase utilitaria: todas las operaciones de resolución son estáticas y sin estado.
    }

    public static String resolve(Throwable error) {
        if (error == null) return UNEXPECTED_ERROR;
        if (error instanceof SincronizacionEnCursoException
                || error instanceof SincronizacionEstadisticasEnCursoException
                || error instanceof ConcurrencyFailureException) return CONCURRENCY_ERROR;
        if (error instanceof TokenMarketException market) return marketCode(market.reason());
        if (error instanceof JugadorNoEncontradoException
                || error instanceof CotizacionNoEncontradaException) return NOT_FOUND;
        if (error instanceof SincronizacionPersistenciaException
                || error instanceof DataAccessException) return PERSISTENCE_ERROR;
        if (error instanceof FootballDataException || error instanceof WhoScoredStatisticsException) {
            return EXTERNAL_PROVIDER_ERROR;
        }
        if (error instanceof IllegalArgumentException) return VALIDATION_ERROR;
        return UNEXPECTED_ERROR;
    }

    public static boolean isControlledCode(String code) {
        return VALIDATION_ERROR.equals(code) || AUTHENTICATION_ERROR.equals(code)
                || AUTHORIZATION_ERROR.equals(code) || NOT_FOUND.equals(code)
                || CONFLICT.equals(code) || CONCURRENCY_ERROR.equals(code)
                || EXTERNAL_PROVIDER_ERROR.equals(code) || PERSISTENCE_ERROR.equals(code)
                || UNEXPECTED_ERROR.equals(code);
    }

    private static String marketCode(TokenMarketException.Reason reason) {
        return switch (reason) {
            case ACCOUNT_NOT_FOUND, OPERATION_NOT_FOUND, OFFER_NOT_FOUND -> NOT_FOUND;
            case IDEMPOTENCY_CONFLICT, STALE_REQUEST, QUOTE_CHANGED,
                    OFFER_ALREADY_CANCELLED, OFFER_NOT_PURCHASABLE,
                    INSUFFICIENT_AVAILABLE_QUANTITY, INSUFFICIENT_CREDITS,
                    INSUFFICIENT_BALANCE, INSUFFICIENT_INVENTORY, INACTIVE_PLAYER,
                    QUOTE_UNAVAILABLE, HISTORICAL_RESULT_UNAVAILABLE -> CONFLICT;
            case INVALID_INPUT, MANUAL_PRICE_NOT_ALLOWED -> VALIDATION_ERROR;
            case MARKET_NOT_READY -> EXTERNAL_PROVIDER_ERROR;
        };
    }
}
