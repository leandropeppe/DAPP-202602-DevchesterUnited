package com.devchesterunited.devchesterunitedv2.unit.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.*;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionEjecucionConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValorizacionEjecucionConfigTest {
    @Test
    void requiereConfiguracionExplicita() {
        var config = new ValorizacionEjecucionConfig(false, "", "");
        assertThatThrownBy(config::configuracionElegibilidad)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exponeLosUmbralesConfiguradosSinDefault() {
        var configuracion = new ValorizacionEjecucionConfig(true, "2", "180").configuracionElegibilidad();
        assertThat(configuracion.minimoPartidos()).isEqualTo(2);
        assertThat(configuracion.minimoMinutos()).isEqualTo(180);
    }
}
