package com.devchesterunited.devchesterunitedv2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.controller.dto.FiltroJugadoresDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.JugadoresResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.impl.JugadorServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.interfaces.JugadorService;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RevocacionEnCursoE2ETest extends PostgresTestSupport {
    private static final String RUTA = "/api/v1/players";
    private static final String PASSWORD = "Catalogo123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired GateConsulta gate;
    @Autowired DataSource dataSource;

    @Test
    void consultaAutorizadaAntesDeRevocacionPuedeTerminarPeroLasSiguientesSon401() throws Exception {
        Credenciales credenciales = registrarYGenerarClave();
        gate.armar();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var consulta = executor.submit(() ->
                    mvc.perform(consulta(credenciales.apiKey()))
                            .andReturn()
                            .getResponse()
                            .getStatus());

            assertThat(gate.autorizacionAlcanzada.await(10, TimeUnit.SECONDS)).isTrue();

            String nueva = generarNuevaClave(credenciales.jwt());
            assertThat(nueva).isNotEqualTo(credenciales.apiKey());

            gate.liberar();
            assertThat(consulta.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }

        mvc.perform(consulta(credenciales.apiKey()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("No autorizado."));
    }

    @Test
    void baseDeDatosInaccesibleDuranteValidacionDevuelve503() throws Exception {
        Credenciales credenciales = registrarYGenerarClave();
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);

        ((HikariDataSource) dataSource).close();

        mvc.perform(consulta(credenciales.apiKey()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("El servicio no está disponible temporalmente."));
    }

    private MockHttpServletRequestBuilder consulta(String apiKey) {
        return get(RUTA).servletPath(RUTA).header("X-API-KEY", apiKey);
    }

    private Credenciales registrarYGenerarClave() throws Exception {
        String nombre = "rev" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "username", nombre,
                        "email", nombre + "@example.test",
                        "password", PASSWORD))))
                .andExpect(status().isCreated());

        JsonNode login = json(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "username", nombre,
                        "password", PASSWORD))))
                .andExpect(status().isOk()));

        String jwt = login.path("data").path("access_token").asText();
        return new Credenciales(jwt, generarNuevaClave(jwt));
    }

    private String generarNuevaClave(String jwt) throws Exception {
        JsonNode respuesta = json(mvc.perform(post("/api/v1/auth/apikey")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()));
        return respuesta.path("data").path("api_key").asText();
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return mapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private record Credenciales(String jwt, String apiKey) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionDePrueba {
        @Bean
        GateConsulta gateConsulta(JugadorServiceImpl real) {
            return new GateConsulta(real);
        }

        @Bean
        @Primary
        JugadorService jugadorServiceConGate(GateConsulta gate) {
            return gate;
        }
    }

    static final class GateConsulta implements JugadorService {
        private final JugadorService real;
        private final CountDownLatch autorizacionAlcanzada = new CountDownLatch(1);
        private final CountDownLatch continuar = new CountDownLatch(1);
        private volatile boolean armado;

        GateConsulta(JugadorService real) {
            this.real = real;
        }

        void armar() {
            armado = true;
        }

        void liberar() {
            continuar.countDown();
        }

        @Override
        public JugadoresResponseDTO consultar(FiltroJugadoresDTO filtros) {
            if (armado) {
                autorizacionAlcanzada.countDown();
                try {
                    if (!continuar.await(30, TimeUnit.SECONDS)) {
                        throw new AssertionError("La barrera de consulta no fue liberada.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("La consulta fue interrumpida.", exception);
                }
            }
            return real.consultar(filtros);
        }
    }
}
