package com.devchesterunited.devchesterunitedv2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.adapter.impl.FootballDataAdapter;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T091: pruebas nuevas de la política de seguridad vigente. No reemplaza las suites históricas. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityPolicyE2ETest extends PostgresTestSupport {
    private static final String PASSWORD = "SecurityPolicy123!";
    private static final Instant AHORA = Instant.parse("2026-09-18T12:00:00Z");
    private static final long EQUIPO_BASE = 700_000_000L;
    private static final long JUGADOR_BASE = 710_000_000L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FootballDataAdapter adapter;

    private final List<UUID> usuarios = new ArrayList<>();

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM jugadores WHERE external_id >= ?", EQUIPO_BASE);
        jdbc.update("DELETE FROM equipos WHERE external_id >= ?", EQUIPO_BASE);
        for (UUID usuario : usuarios) {
            jdbc.update("DELETE FROM usuarios WHERE id = ?", usuario);
        }
        usuarios.clear();
        reset(adapter);
    }

    @Test
    void jugadoresSinCredencialesSonPublicos() throws Exception {
        mvc.perform(get("/api/v1/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void jugadoresNoRequierenApiKey() throws Exception {
        mvc.perform(get("/api/v1/players").header("X-API-KEY", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void sincronizacionSinApiKeyEs401() throws Exception {
        mvc.perform(post("/api/v1/catalog/sync"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sincronizacionConApiKeyInvalidaEs401() throws Exception {
        mvc.perform(post("/api/v1/catalog/sync")
                        .header("X-API-KEY", "dev_key_" + "a".repeat(43)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sincronizacionConApiKeyValidaSuperaAutenticacion() throws Exception {
        when(adapter.sincronizar()).thenReturn(snapshot());
        String apiKey = registrarLoginYGenerarApiKey();

        mvc.perform(post("/api/v1/catalog/sync").header("X-API-KEY", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.teams_processed").value(96))
                .andExpect(jsonPath("$.data.players_processed").value(96));

        verify(adapter).sincronizar();
    }

    @Test
    void jwtPorSiSoloNoAutorizaSincronizacion() throws Exception {
        String jwt = registrarYLogin();

        mvc.perform(post("/api/v1/catalog/sync")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generacionApiKeySinJwtEs401() throws Exception {
        mvc.perform(post("/api/v1/auth/apikey"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generacionApiKeyConJwtValidoSigueFuncionando() throws Exception {
        String jwt = registrarYLogin();

        JsonNode respuesta = json(mvc.perform(post("/api/v1/auth/apikey")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()));

        assertThat(respuesta.path("status").asText()).isEqualTo("success");
        assertThat(respuesta.path("data").path("api_key").asText())
                .matches("dev_key_[A-Za-z0-9_-]{43}");
    }

    private String registrarLoginYGenerarApiKey() throws Exception {
        String jwt = registrarYLogin();
        return json(mvc.perform(post("/api/v1/auth/apikey")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())).path("data").path("api_key").asText();
    }

    private String registrarYLogin() throws Exception {
        String username = "sec" + UUID.randomUUID().toString().replace("-", "").substring(0, 17);
        String email = username + "@example.test";
        JsonNode registro = json(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "username", username, "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()));
        usuarios.add(UUID.fromString(registro.path("data").path("id").asText().substring(4)));

        return json(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "username", username, "password", PASSWORD))))
                .andExpect(status().isOk())).path("data").path("access_token").asText();
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return mapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private static CatalogoSincronizado snapshot() {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Equipo> equipos = new ArrayList<>();
        List<Jugador> jugadores = new ArrayList<>();
        for (int i = 0; i < 96; i++) {
            int liga = i < 20 ? 0 : i < 40 ? 1 : i < 58 ? 2 : i < 78 ? 3 : 4;
            UUID equipoId = UUID.randomUUID();
            equipos.add(Equipo.importado(equipoId, EQUIPO_BASE + i, "Equipo seguridad " + i,
                    "E", "E", ligas.get(liga), null, 2026, null, null, AHORA));
            jugadores.add(new Jugador(UUID.randomUUID(),
                    "Jugador seguridad " + i, equipoId, "Forward", null, "Argentina", AHORA));
        }
        Map<UUID, Long> ids = new HashMap<>();
        for (int i = 0; i < jugadores.size(); i++) ids.put(jugadores.get(i).getId(), JUGADOR_BASE + i);
        return new CatalogoSincronizado(equipos, jugadores, ids);
    }
}
