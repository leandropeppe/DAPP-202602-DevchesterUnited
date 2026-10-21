package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstrategiaVisibleRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.EstrategiaValorizacionBootstrap;
import com.devchesterunited.devchesterunitedv2.service.interfaces.ValorizacionJugadorService;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EstrategiaValorizacionBootstrapIntegrationTest extends PostgresTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EstrategiaValorizacionBootstrap bootstrap;
    @Autowired private EstrategiaValorizacionRepository estrategias;
    @Autowired private EstrategiaVisibleRepository visibles;
    @Autowired private ValorizacionJugadorService valorizacion;

    @BeforeEach
    void dejarBaseSinEstrategias() {
        jdbc.execute("TRUNCATE resultado_elegibilidad_valorizacion, historico_cotizacion_estrategia, "
                + "cotizacion_actual_estrategia, estrategia_visible, ejecuciones_valorizacion, "
                + "estrategias_valorizacion CASCADE");
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE resultado_elegibilidad_valorizacion, historico_cotizacion_estrategia, "
                + "cotizacion_actual_estrategia, estrategia_visible, ejecuciones_valorizacion, "
                + "estrategias_valorizacion CASCADE");
    }

    @Test
    void inicializaLasTresEstrategiasYLaVisiblePosicionalV2() {
        bootstrap.sincronizar();

        assertThat(estrategias.findById(new com.devchesterunited.devchesterunitedv2.persistence.dto.EstrategiaValorizacionKey("GENERAL", 1)))
                .get().satisfies(e -> {
                    assertThat(e.isHabilitadaParaCalculo()).isTrue();
                    assertThat(e.isEsProvisoria()).isFalse();
                    assertThat(e.getValorBase()).isEqualByComparingTo("1");
                    assertThat(e.getFactorEscala()).isEqualByComparingTo("99");
                    assertThat(e.getConfiguracionJson()).contains("GOALS", "0.25");
                });
        assertThat(estrategias.findById(new com.devchesterunited.devchesterunitedv2.persistence.dto.EstrategiaValorizacionKey("POSITIONAL", 1)))
                .get().satisfies(e -> {
                    assertThat(e.isHabilitadaParaCalculo()).isTrue();
                    assertThat(e.isEsProvisoria()).isFalse();
                    assertThat(e.getConfiguracionJson()).contains("\"PO\"", "\"provisoria\":true",
                            "\"factoresMercado\":{\"DL\":1.15,\"MC\":1.05,\"DF\":0.95,\"PO\":0.90}");
                });
        assertThat(estrategias.findById(new com.devchesterunited.devchesterunitedv2.persistence.dto.EstrategiaValorizacionKey("POSITIONAL", 2)))
                .get().satisfies(e -> {
                    assertThat(e.isHabilitadaParaCalculo()).isTrue();
                    assertThat(e.getConfiguracionJson()).doesNotContain("RATING").contains("\"DL\":1");
                });
        assertThat(visibles.buscar()).get().satisfies(v -> {
            assertThat(v.getEstrategiaId()).isEqualTo("POSITIONAL");
            assertThat(v.getEstrategiaVersion()).isEqualTo(2);
        });
    }

    @Test
    void segundaInicializacionEsIdempotenteYLaValorizacionNoViolaFks() {
        bootstrap.sincronizar();
        bootstrap.sincronizar();

        assertThat(estrategias.count()).isEqualTo(3);
        assertThat(visibles.count()).isEqualTo(1);
        assertThatCode(() -> valorizacion.ejecutarManual(ConfiguracionElegibilidad.sinUmbral()))
                .doesNotThrowAnyException();
    }
}
