package com.devchesterunited.devchesterunitedv2.exception;

/** Error de aplicación para una respuesta inválida o indisponibilidad del proveedor estadístico. */
public class EstadisticasProveedorException extends RuntimeException {
    public EstadisticasProveedorException(String message) {
        super(message);
    }

    public EstadisticasProveedorException(String message, Throwable cause) {
        super(message, cause);
    }
}
