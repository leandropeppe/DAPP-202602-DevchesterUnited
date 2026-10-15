package com.devchesterunited.devchesterunitedv2.service.dto;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoMercado;
import java.math.BigDecimal;

/** Resultado inmutable de una ejecución del proceso de inicialización del mercado. */
public record InicializacionMercadoResultado(
        EstadoMercado estado,
        long epoca,
        BigDecimal reservaInicialEfectiva) { }
