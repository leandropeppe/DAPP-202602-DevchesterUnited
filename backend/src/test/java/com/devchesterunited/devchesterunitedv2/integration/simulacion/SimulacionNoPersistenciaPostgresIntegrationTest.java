package com.devchesterunited.devchesterunitedv2.integration.simulacion;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "valorizacion.elegibilidad.configurada=true")
class SimulacionNoPersistenciaPostgresIntegrationTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void POSTNoCreaEjecucionesNiCotizacionesNiHistoricos() throws Exception {
        Map<String, Long> antes = conteos();
        mvc.perform(post("/api/v1/players/ply_" + UUID.randomUUID() + "/valuation-simulator")
                        .contentType("application/json")
                        .content("{\"horizonMatches\":5,\"modifications\":{}}"))
                .andExpect(status().isNotFound());
        assertThat(conteos()).isEqualTo(antes);
    }

    private Map<String, Long> conteos() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : new String[]{"jugador_estadisticas", "cotizacion_actual_estrategia",
                "historico_cotizacion_estrategia", "ejecuciones_valorizacion"}) {
            result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return result;
    }
}
