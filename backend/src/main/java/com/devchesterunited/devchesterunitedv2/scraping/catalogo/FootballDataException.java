package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

/** Error interno estable para fallos de contrato, transporte o cuota del proveedor. */
public class FootballDataException extends RuntimeException {
    public FootballDataException(String message) { super(message); }
    public FootballDataException(String message, Throwable cause) { super(message, cause); }
}
