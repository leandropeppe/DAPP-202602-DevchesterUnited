package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public final class MetricaNoEditableException extends ExcepcionSimulacion {
    public MetricaNoEditableException(String metrica) {
        super(CodigoErrorSimulacion.METRICA_NO_EDITABLE,
                "La métrica no es editable en el escenario: " + metrica);
    }
}
