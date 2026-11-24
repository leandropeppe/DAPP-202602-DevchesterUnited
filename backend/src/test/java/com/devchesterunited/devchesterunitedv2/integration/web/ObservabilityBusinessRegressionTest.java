package com.devchesterunited.devchesterunitedv2.integration.web;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression minima de contratos de negocio afectados solo de forma transversal por observabilidad. */
@ActiveProfiles("test")
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc(printOnlyOnFailure = false,
        print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class ObservabilityBusinessRegressionTest extends PostgresTestSupport {
    private static final String PLAYERS_PATH = "/api/v1/players";
    private static final String TRANSACTIONS_PATH = "/api/v1/market/transactions";
    private static final String PASSWORD = "Observability123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> fixtureUsers = new ArrayList<>();

    @Test
    void publicPlayersContractKeepsItsResponseEnvelope() throws Exception {
        mvc.perform(get(PLAYERS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.players").isArray())
                .andExpect(jsonPath("$.data.pagination.current_page").value(1))
                .andExpect(jsonPath("$.data.pagination.page_size").value(20));
    }

    @Test
    void authenticatedTransactionHistoryKeepsItsResponseEnvelope() throws Exception {
        String jwt = registerAndLogin();

        mvc.perform(get(TRANSACTIONS_PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.transactions").isArray())
                .andExpect(jsonPath("$.data.pagination.current_page").value(1))
                .andExpect(jsonPath("$.data.pagination.page_size").value(20));
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
        JsonNode registration = responseOf(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "username", username,
                        "email", username + "@example.test",
                        "password", PASSWORD)))
                .accept(MediaType.APPLICATION_JSON), status().isCreated());
        fixtureUsers.add(UUID.fromString(registration.path("data").path("id").asText().substring(4)));

        JsonNode login = responseOf(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD)))
                .accept(MediaType.APPLICATION_JSON), status().isOk());
        String accessToken = login.path("data").path("access_token").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private JsonNode responseOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        String response = mvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(response);
    }
}
