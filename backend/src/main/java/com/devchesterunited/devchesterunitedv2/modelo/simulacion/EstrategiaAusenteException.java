package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public final class EstrategiaAusenteException extends ExcepcionSimulacion {
    public EstrategiaAusenteException(String estrategia) {
        super(CodigoErrorSimulacion.ESTRATEGIA_AUSENTE, "La estrategia no está disponible: " + estrategia);
    }
}
