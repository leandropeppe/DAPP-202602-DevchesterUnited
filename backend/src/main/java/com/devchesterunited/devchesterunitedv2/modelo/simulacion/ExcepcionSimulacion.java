package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

public class ExcepcionSimulacion extends IllegalArgumentException {
    private final CodigoErrorSimulacion codigo;

    public ExcepcionSimulacion(CodigoErrorSimulacion codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CodigoErrorSimulacion codigo() {
        return codigo;
    }
}
