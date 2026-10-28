package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public final class DatosInsuficientesException extends ExcepcionSimulacion {
    public DatosInsuficientesException(String dato) {
        super(CodigoErrorSimulacion.DATOS_INSUFICIENTES,
                "Faltan datos obligatorios para proyectar: " + dato);
    }
}
