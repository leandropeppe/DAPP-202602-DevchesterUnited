package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

/** Error interno de obtención o traducción de WhoScored. */
public class WhoScoredStatisticsException extends RuntimeException {
    public WhoScoredStatisticsException(String message) { super(message); }
    public WhoScoredStatisticsException(String message, Throwable cause) { super(message, cause); }
}
