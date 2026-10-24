package com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas;

import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SincronizacionResultado;

public interface SincronizacionCatalogoService {
    void validarUsuario(java.util.UUID usuarioId);
    SincronizacionResultado sincronizar();
}
