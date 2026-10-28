package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public final class RangoMetricaSimulacionException extends ExcepcionSimulacion {
    public RangoMetricaSimulacionException(String metrica, Object valor) {
        super(CodigoErrorSimulacion.VALOR_FUERA_DE_RANGO,
                "El valor de " + metrica + " no respeta el rango permitido: " + valor);
    }
}
