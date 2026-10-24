package com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion;

public interface EstrategiaVisibleService {
    void seleccionar(String estrategiaId, int version);
    SeleccionVisible obtener();

    record SeleccionVisible(String estrategiaId, int version) { }
}
