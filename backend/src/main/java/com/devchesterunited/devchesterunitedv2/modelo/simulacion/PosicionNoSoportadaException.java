package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public final class PosicionNoSoportadaException extends ExcepcionSimulacion {
    public PosicionNoSoportadaException(String posicion) {
        super(CodigoErrorSimulacion.POSICION_NO_SOPORTADA, "La posición no está soportada: " + posicion);
    }
}
