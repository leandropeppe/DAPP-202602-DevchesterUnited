package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;

/** Puerto de acceso al proveedor que entrega el snapshot del catalogo. */
public interface ProveedorCatalogo {
    CatalogoSincronizado sincronizar();
}
