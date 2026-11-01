package com.devchesterunited.devchesterunitedv2.e2e.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaVisibleRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
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
class HistoricoCotizacionE2ETest extends PostgresTestSupport {
    private static final UUID PLAYER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EXECUTION = UUID.fromString("36000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mvc;
    @Autowired EstrategiaValorizacionRepository estrategias;
    @Autowired EstrategiaVisibleRepository visibles;
    @Autowired EjecucionValorizacionRepository ejecuciones;
    @Autowired HistoricoCotizacionEstrategiaRepository historicos;

    @BeforeEach
    void preparar() {
        historicos.deleteAll(); visibles.deleteAll(); ejecuciones.deleteAll(); estrategias.deleteAll();
        estrategias.save(EstrategiaValorizacionEntity.builder().id("GENERAL").version(1)
                .nombre("General").tipo("GENERAL").habilitadaParaCalculo(true)
                .esProvisoria(false).valorBase(BigDecimal.ZERO).factorEscala(BigDecimal.ONE)
                .configuracionJson("{}").build());
        estrategias.save(EstrategiaValorizacionEntity.builder().id("POSITIONAL").version(1)
                .nombre("Positional").tipo("POSICIONAL").habilitadaParaCalculo(true)
                .esProvisoria(false).valorBase(BigDecimal.ZERO).factorEscala(BigDecimal.ONE)
                .configuracionJson("{}").build());
        visibles.save(new EstrategiaVisibleEntity("POSITIONAL", 1, Instant.now()));
        ejecuciones.save(new EjecucionValorizacionEntity(EXECUTION, "MANUAL", Instant.now(), Instant.now(), "COMPLETADA"));
        Instant reciente = Instant.now();
        Instant anterior = reciente.minusSeconds(60);
        historicos.save(HistoricoCotizacionEstrategiaEntity.builder().id(UUID.randomUUID())
                .jugadorId(PLAYER).estrategiaId("POSITIONAL").version(1).ejecucionId(EXECUTION)
                .calculadaAt(reciente).score(d(".80")).cotizacion(d("80"))
                .valorBase(BigDecimal.ZERO).factorEscala(BigDecimal.ONE)
                .trazabilidadJson("{\"order\":\"recent\"}").build());
        historicos.save(HistoricoCotizacionEstrategiaEntity.builder().id(UUID.randomUUID())
                .jugadorId(PLAYER).estrategiaId("POSITIONAL").version(1).ejecucionId(EXECUTION)
                .calculadaAt(anterior).score(d(".70")).cotizacion(d("70"))
                .valorBase(BigDecimal.ZERO).factorEscala(BigDecimal.ONE)
                .trazabilidadJson("{\"order\":\"old\"}").build());
        historicos.save(HistoricoCotizacionEstrategiaEntity.builder().id(UUID.randomUUID())
                .jugadorId(PLAYER).estrategiaId("GENERAL").version(1).ejecucionId(EXECUTION)
                .calculadaAt(reciente).score(d(".40")).cotizacion(d("40"))
                .valorBase(BigDecimal.ZERO).factorEscala(BigDecimal.ONE)
                .trazabilidadJson("{\"source\":\"general\"}").build());
    }

    @AfterEach
    void limpiar() { historicos.deleteAll(); visibles.deleteAll(); ejecuciones.deleteAll(); estrategias.deleteAll(); }

    @Test
    void sinSeleccionDevuelveSoloSerieVisibleOrdenadaYExplicitaSoloGeneral() throws Exception {
        JsonNode visible = json(mvc.perform(get("/api/v1/players/ply_" + PLAYER + "/quotes")).andExpect(status().isOk()));
        assertThat(visible.path("data").path("items")).hasSize(2);
        assertThat(visible.path("data").path("items").get(0).path("score").decimalValue()).isEqualByComparingTo(".80");
        assertThat(visible.path("data").path("items").get(1).path("score").decimalValue()).isEqualByComparingTo(".70");
        assertThat(visible.path("data").path("items").get(0).path("strategyId").asText()).isEqualTo("POSITIONAL");

        JsonNode general = json(mvc.perform(get("/api/v1/players/ply_" + PLAYER + "/quotes")
                .param("strategyId", "GENERAL").param("strategyVersion", "1")).andExpect(status().isOk()));
        assertThat(general.path("data").path("items")).hasSize(1);
        assertThat(general.path("data").path("items").get(0).path("strategyId").asText()).isEqualTo("GENERAL");
    }

    @Test
    void rutaAnteriorYaNoEstaExpuestaYPaginacionSeConserva() throws Exception {
        mvc.perform(get("/api/v1/players/ply_" + PLAYER + "/valuation/history"))
                .andExpect(status().isNotFound());

        JsonNode pagina = json(mvc.perform(get("/api/v1/players/ply_" + PLAYER + "/quotes")
                .param("page", "2").param("limit", "1")).andExpect(status().isOk()));
        assertThat(pagina.path("data").path("items")).hasSize(1);
        assertThat(pagina.path("data").path("total").asLong()).isEqualTo(2);
        assertThat(pagina.path("data").path("page").asInt()).isEqualTo(2);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return new ObjectMapper().readTree(result.andReturn().getResponse().getContentAsString());
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
