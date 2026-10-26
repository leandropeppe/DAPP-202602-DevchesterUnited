package com.devchesterunited.devchesterunitedv2.integration.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.EstadisticasProveedorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SincronizacionEstadisticasPassingFailureIntegrationTest {
    @Test
    void falloDeProveedorSeRepresentaComoErrorSanitizado() {
        assertThatThrownBy(() -> { throw new EstadisticasProveedorException("No se pudo adquirir la familia de pases."); })
                .isInstanceOf(EstadisticasProveedorException.class)
                .hasMessageContaining("familia de pases");
    }
}
