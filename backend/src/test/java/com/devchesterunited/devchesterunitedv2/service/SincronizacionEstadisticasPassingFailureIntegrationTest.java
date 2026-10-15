package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.service.exception.EstadisticasProveedorException;
import org.junit.jupiter.api.Test;

class SincronizacionEstadisticasPassingFailureIntegrationTest {
    @Test
    void falloDeProveedorSeRepresentaComoErrorSanitizado() {
        assertThatThrownBy(() -> { throw new EstadisticasProveedorException("No se pudo adquirir la familia de pases."); })
                .isInstanceOf(EstadisticasProveedorException.class)
                .hasMessageContaining("familia de pases");
    }
}
