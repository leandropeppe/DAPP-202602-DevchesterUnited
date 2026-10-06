package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;

/** Puerto de acceso al proveedor que entrega el snapshot del catalogo. */
public interface ProveedorCatalogo {
    CatalogoSincronizado sincronizar();
}
