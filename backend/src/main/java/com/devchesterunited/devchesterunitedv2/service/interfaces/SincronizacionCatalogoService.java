package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.impl.SincronizacionResultado;

public interface SincronizacionCatalogoService {
    void validarUsuario(java.util.UUID usuarioId);
    SincronizacionResultado sincronizar();
}
