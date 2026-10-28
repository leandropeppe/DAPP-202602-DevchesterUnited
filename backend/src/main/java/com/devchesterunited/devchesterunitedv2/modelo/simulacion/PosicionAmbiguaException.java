package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public final class PosicionAmbiguaException extends ExcepcionSimulacion {
    public PosicionAmbiguaException(String posicion) {
        super(CodigoErrorSimulacion.POSICION_AMBIGUA, "La posición es ambigua: " + posicion);
    }
}
