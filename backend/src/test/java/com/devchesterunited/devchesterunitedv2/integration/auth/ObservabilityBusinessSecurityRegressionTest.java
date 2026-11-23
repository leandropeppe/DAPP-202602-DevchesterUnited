package com.devchesterunited.devchesterunitedv2.integration.auth;

import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.ValorizacionEjecucionPort;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityBusinessSecurityRegressionTest extends PostgresTestSupport {

    private static final String PASSWORD = "ObservabilitySecurity123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ValorizacionEjecucionPort valorizacionEjecucion;

    private final List<UUID> fixtureUsers = new ArrayList<>();

    @Test
    void apiKeyIssuanceKeepsJwtAuthenticationRules() throws Exception {
        mvc.perform(post("/api/v1/auth/apikey"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/apikey").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        String jwt = registerAndLogin();
        String apiKey = apiKeyFor(jwt);

        assertThat(apiKey).matches("dev_key_[A-Za-z0-9_-]{43}");
    }

    @Test
    void marketTransactionsKeepsJwtBusinessAuthenticationRules() throws Exception {
        String jwt = registerAndLogin();

        mvc.perform(get("/api/v1/market/transactions").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    void quoteRecalculationKeepsApiKeyAuthenticationRules() throws Exception {
        mvc.perform(post("/api/v1/quotes/recalculate"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/quotes/recalculate").header("X-API-KEY", "dev_key_" + "a".repeat(43)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/quotes/recalculate").header("X-API-KEY", apiKeyFor(registerAndLogin())))
                .andExpect(status().isOk());

        verify(valorizacionEjecucion).ejecutarSemanal();
    }

    @AfterEach
    void cleanAuthenticationFixtures() {
        for (UUID userId : fixtureUsers) {
            jdbc.update("DELETE FROM api_keys WHERE user_id = ?", userId);
            limpiarUsuarioYDependencias(jdbc, userId);
        }
        fixtureUsers.clear();
    }

    private String registerAndLogin() throws Exception {
        String username = "obs" + UUID.randomUUID().toString().replace("-", "").substring(0, 17);
        JsonNode registration = json(mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "email", username + "@example.test",
                        "password", PASSWORD))))
                .andExpect(status().isCreated()));
        fixtureUsers.add(UUID.fromString(registration.path("data").path("id").asText().substring(4)));

        return json(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", PASSWORD))))
                .andExpect(status().isOk()))
                .path("data").path("access_token").asText();
    }

    private String apiKeyFor(String jwt) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/apikey").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()))
                .path("data").path("api_key").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
