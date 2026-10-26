package com.devchesterunited.devchesterunitedv2.e2e.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyE2ETest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder jwtEncoder;
    @MockitoBean com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionScheduler scheduler;
    @Value("${spring.datasource.url}") String jdbcUrl;
    private final List<String> usuarios = new ArrayList<>();

    @Test
    void generaYRotaUnaClaveConRespuestaCompletaNoStoreYSecretoUnaSolaVez() throws Exception {
        String jwt = registrarYLogin().jwt();
        JsonNode primera = cuerpo(generar(jwt).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store"))));
        assertThat(primera.path("status").asText()).isEqualTo("success");
        assertThat(primera.path("message").asText()).contains("API Key");
        JsonNode datos = primera.path("data");
        assertThat(datos.fieldNames()).toIterable().containsExactlyInAnyOrder("api_key", "prefix", "is_active", "created_at");
        String secreto = datos.path("api_key").asText();
        assertThat(secreto).matches("dev_key_[A-Za-z0-9_-]{43}");
        assertThat(datos.path("prefix").asText()).isEqualTo(secreto.substring(0, 14));
        assertThat(datos.path("is_active").asBoolean()).isTrue();
        assertThat(cuerpo(generar(jwt).andExpect(status().isOk())).toString()).doesNotContain(secreto);
    }

    @Test
    void rechazaCuerpoNoVacioYApiKeySolaNoAutoriza() throws Exception {
        String jwt = registrarYLogin().jwt();
        error(mvc.perform(post("/api/v1/auth/apikey").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 400);
        error(mvc.perform(post("/api/v1/auth/apikey").header("X-API-KEY", "dev_key_" + "a".repeat(43))), 401);
        generar(jwt).andExpect(status().isOk());
    }

    @Test
    void jwtAusenteAlteradoExpiradoYOUsuarioInexistenteSon401() throws Exception {
        String valido = registrarYLogin().jwt();
        error(generarSinAuth(), 401);
        error(generar("no.es.un.jwt"), 401);
        var ahora = java.time.Instant.now().minusSeconds(7200);
        String expirado = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), JwtClaimsSet.builder()
                        .issuer("devchester-united").audience(List.of("devchester-api"))
                        .subject("usr_00000000-0000-0000-0000-000000000000")
                        .issuedAt(ahora).expiresAt(ahora.plusSeconds(3600)).build())).getTokenValue();
        error(generar(expirado), 401);
        String inexistente = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIn0.invalid";
        error(generar(inexistente), 401);
        assertThat(valido).isNotBlank();
    }

    @Test
    void recalculoRequiereApiKeyYDelegaEnElScheduler() throws Exception {
        error(mvc.perform(post("/api/v1/quotes/recalculate")), 401);
        error(mvc.perform(post("/api/v1/quotes/recalculate").header("X-API-KEY", "dev_key_" + "a".repeat(43))), 401);

        String apiKey = cuerpo(generar(registrarYLogin().jwt()).andExpect(status().isOk()))
                .path("data").path("api_key").asText();
        cuerpo(mvc.perform(post("/api/v1/quotes/recalculate").header("X-API-KEY", apiKey))
                .andExpect(status().isOk()));

        verify(scheduler).ejecutarSemanal();
    }

    @Test
    void competenciaDeRotacionExpone409SinSecretoEnElError() throws Exception {
        Credenciales credenciales = registrarYLogin();
        try (var conexion = DriverManager.getConnection(jdbcUrl)) {
            conexion.setAutoCommit(false);
            try {
                try (var consulta = conexion.prepareStatement(
                        "SELECT id FROM usuarios WHERE id = ? FOR UPDATE")) {
                    consulta.setObject(1, credenciales.usuarioId());
                    try (var resultado = consulta.executeQuery()) {
                        assertThat(resultado.next()).isTrue();
                    }
                }

                JsonNode conflicto = cuerpo(generar(credenciales.jwt())
                        .andExpect(status().isConflict()));
                assertThat(conflicto.path("status").asText()).isEqualTo("error");
                assertThat(conflicto.path("message").asText())
                        .isEqualTo("La solicitud entra en conflicto con el estado actual del recurso.");
                assertThat(conflicto.toString())
                        .doesNotContain("dev_key_", "api_key", "access_token", "Segura123!");
            } finally {
                conexion.rollback();
            }
        }

        JsonNode posterior = cuerpo(generar(credenciales.jwt()).andExpect(status().isOk()));
        assertThat(posterior.path("data").path("api_key").asText())
                .matches("dev_key_[A-Za-z0-9_-]{43}");
    }

    private Credenciales registrarYLogin() throws Exception {
        String nombre = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        usuarios.add(nombre);
        JsonNode registro = cuerpo(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", nombre, "email", nombre + "@example.com", "password", "Segura123!"))))
                .andExpect(status().isCreated()));
        String jwt = cuerpo(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", nombre, "password", "Segura123!"))))
                .andExpect(status().isOk())).path("data").path("access_token").asText();
        String id = registro.path("data").path("id").asText();
        assertThat(id).startsWith("usr_");
        return new Credenciales(jwt, UUID.fromString(id.substring(4)));
    }

    private ResultActions generar(String jwt) throws Exception {
        return mvc.perform(post("/api/v1/auth/apikey").header("Authorization", "Bearer " + jwt));
    }
    private ResultActions generarSinAuth() throws Exception { return mvc.perform(post("/api/v1/auth/apikey")); }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        String texto = resultado.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn()
                .getResponse().getContentAsString();
        assertThat(texto).doesNotContain("Segura123!", "password_hash", "JWT_SECRET", "NO_FILTRAR");
        return mapper.readTree(texto);
    }
    private JsonNode error(ResultActions resultado, int codigo) throws Exception {
        JsonNode json = cuerpo(resultado.andExpect(status().is(codigo)));
        assertThat(json.path("status").asText()).isEqualTo("error");
        assertThat(json.path("message").asText()).matches("(?s).*[A-Za-zÁÉÍÓÚáéíóú].*");
        return json;
    }

    private record Credenciales(String jwt, UUID usuarioId) { }

    @AfterEach void limpiar() { /* La suite real limpia sus fixtures al hacer rollback/BD descartable. */ }
}
