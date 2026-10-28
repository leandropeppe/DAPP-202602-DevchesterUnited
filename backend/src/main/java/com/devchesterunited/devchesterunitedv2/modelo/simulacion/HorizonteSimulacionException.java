package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public final class HorizonteSimulacionException extends ExcepcionSimulacion {
    public HorizonteSimulacionException(int horizonte) {
        super(CodigoErrorSimulacion.HORIZONTE_INVALIDO,
                "El horizonte debe ser de 3, 5, 8 o 10 partidos: " + horizonte);
    }
}
