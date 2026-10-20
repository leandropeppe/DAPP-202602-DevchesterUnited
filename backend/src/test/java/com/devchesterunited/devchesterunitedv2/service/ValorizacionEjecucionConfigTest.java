package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.service.config.ValorizacionEjecucionConfig;
import org.junit.jupiter.api.Test;

class ValorizacionEjecucionConfigTest {
    @Test
    void requiereConfiguracionExplicita() {
        assertThatThrownBy(() -> new ValorizacionEjecucionConfig(false, "", "").configuracionElegibilidad())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exponeLosUmbralesConfiguradosSinDefault() {
        var configuracion = new ValorizacionEjecucionConfig(true, "2", "180").configuracionElegibilidad();
        assertThat(configuracion.minimoPartidos()).isEqualTo(2);
        assertThat(configuracion.minimoMinutos()).isEqualTo(180);
    }
}
