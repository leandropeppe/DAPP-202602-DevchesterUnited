package com.devchesterunited.devchesterunitedv2.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaVisibleRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaVisibleEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.HistoricoCotizacionEstrategiaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class CotizacionActualE2ETest extends PostgresTestSupport {
    private static final UUID PLAYER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EXECUTION = UUID.fromString("35000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mvc;
    @Autowired EstrategiaValorizacionRepository estrategias;
    @Autowired EstrategiaVisibleRepository visibles;
    @Autowired EjecucionValorizacionRepository ejecuciones;
    @Autowired CotizacionActualEstrategiaRepository actuales;
    @Autowired HistoricoCotizacionEstrategiaRepository historicos;

    @BeforeEach
    void preparar() {
        historicos.deleteAll(); actuales.deleteAll(); visibles.deleteAll(); ejecuciones.deleteAll(); estrategias.deleteAll();
        estrategias.save(new EstrategiaValorizacionEntity("GENERAL", 1, "General", "GENERAL", true, false, BigDecimal.ZERO, BigDecimal.ONE, "{}"));
        estrategias.save(new EstrategiaValorizacionEntity("POSITIONAL", 1, "Positional", "POSICIONAL", true, false, BigDecimal.ZERO, BigDecimal.ONE, "{}"));
        visibles.save(new EstrategiaVisibleEntity("POSITIONAL", 1, Instant.now()));
        ejecuciones.save(new EjecucionValorizacionEntity(EXECUTION, "MANUAL", Instant.now(), Instant.now(), "COMPLETADA"));
        Instant ahora = Instant.now();
        actuales.save(new CotizacionActualEstrategiaEntity(PLAYER, "GENERAL", 1, EXECUTION, d(".40"), d("40"), BigDecimal.ZERO, BigDecimal.ONE, ahora));
        actuales.save(new CotizacionActualEstrategiaEntity(PLAYER, "POSITIONAL", 1, EXECUTION, d(".80"), d("80"), BigDecimal.ZERO, BigDecimal.ONE, ahora));
        historicos.save(new HistoricoCotizacionEstrategiaEntity(UUID.randomUUID(), PLAYER, "GENERAL", 1, EXECUTION, ahora, d(".40"), d("40"), BigDecimal.ZERO, BigDecimal.ONE, "{\"source\":\"general\"}"));
        historicos.save(new HistoricoCotizacionEstrategiaEntity(UUID.randomUUID(), PLAYER, "POSITIONAL", 1, EXECUTION, ahora, d(".80"), d("80"), BigDecimal.ZERO, BigDecimal.ONE, "{\"source\":\"positional\"}"));
    }

    @AfterEach
    void limpiar() { historicos.deleteAll(); actuales.deleteAll(); visibles.deleteAll(); ejecuciones.deleteAll(); estrategias.deleteAll(); }

    @Test
    void sinSeleccionUsaVisibleYConSeleccionExplicitaNoMezcla() throws Exception {
        JsonNode visible = json(mvc.perform(get("/api/v1/players/ply_" + PLAYER + "/valuation")).andExpect(status().isOk()));
        assertThat(visible.path("data").path("strategyId").asText()).isEqualTo("POSITIONAL");
        assertThat(visible.path("data").path("trace").path("source").asText()).isEqualTo("positional");

        JsonNode general = json(mvc.perform(get("/api/v1/players/ply_" + PLAYER + "/valuation")
                .param("strategyId", "GENERAL").param("strategyVersion", "1")).andExpect(status().isOk()));
        assertThat(general.path("data").path("strategyId").asText()).isEqualTo("GENERAL");
        assertThat(general.path("data").path("trace").path("source").asText()).isEqualTo("general");
    }

    @Test
    void devuelve404SinCotizacionY400SiFaltaLaVersion() throws Exception {
        mvc.perform(get("/api/v1/players/ply_" + UUID.randomUUID() + "/valuation"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/players/ply_" + PLAYER + "/valuation").param("strategyId", "GENERAL"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return new ObjectMapper().readTree(result.andReturn().getResponse().getContentAsString());
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
