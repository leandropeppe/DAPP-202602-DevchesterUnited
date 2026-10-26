package com.devchesterunited.devchesterunitedv2.integration.valorizacion;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaVisibleEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaVisibleRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.EstrategiaVisibleService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EstrategiaVisibleServiceIntegrationTest extends PostgresTestSupport {
    @Autowired private EstrategiaVisibleService servicio;
    @Autowired private EstrategiaValorizacionRepository estrategias;
    @Autowired private EstrategiaVisibleRepository visibles;
    @Autowired private HistoricoCotizacionEstrategiaRepository historicos;

    @AfterEach
    void limpiar() {
        visibles.deleteAll();
        historicos.deleteAll();
        estrategias.deleteAll();
    }

    @Test
    void cambiaVisibleSinRecalcularNiCrearOModificarHistoricos() {
        prepararEstrategias();
        visibles.save(new EstrategiaVisibleEntity("GENERAL", 1, Instant.now()));
        assertThat(servicio.obtener().estrategiaId()).isEqualTo("GENERAL");

        servicio.seleccionar("POSITIONAL", 1);

        assertThat(servicio.obtener().estrategiaId()).isEqualTo("POSITIONAL");
        assertThat(historicos.count()).isZero();
    }

    @Test
    void rechazaVersionNoHabilitada() {
        prepararEstrategias();

        assertThatThrownBy(() -> servicio.seleccionar("GENERAL", 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void prepararEstrategias() {
        estrategias.save(new EstrategiaValorizacionEntity("GENERAL", 1, "General", "GENERAL", true, false,
                BigDecimal.ZERO, BigDecimal.ONE, "{}"));
        estrategias.save(new EstrategiaValorizacionEntity("POSITIONAL", 1, "Positional", "POSICIONAL", true, false,
                BigDecimal.ZERO, BigDecimal.ONE, "{}"));
    }
}
