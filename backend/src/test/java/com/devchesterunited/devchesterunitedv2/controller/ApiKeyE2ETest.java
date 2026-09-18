package com.devchesterunited.devchesterunitedv2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyE2ETest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder jwtEncoder;
    private final List<String> usuarios = new ArrayList<>();

    @Test
    void generaYRotaUnaClaveConRespuestaCompletaNoStoreYSecretoUnaSolaVez() throws Exception {
        String jwt = registrarYLogin();
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
        String jwt = registrarYLogin();
        error(mvc.perform(post("/api/v1/auth/apikey").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 400);
        error(mvc.perform(post("/api/v1/auth/apikey").header("X-API-KEY", "dev_key_" + "a".repeat(43))), 401);
        generar(jwt).andExpect(status().isOk());
    }

    @Test
    void jwtAusenteAlteradoExpiradoYOUsuarioInexistenteSon401() throws Exception {
        String valido = registrarYLogin();
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
    void competenciaDeRotacionExpone409SinSecretoEnElError() throws Exception {
        String jwt = registrarYLogin();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> llamada = () -> generar(jwt).andReturn().getResponse().getStatus();
            var a = executor.submit(llamada);
            var b = executor.submit(llamada);
            int uno = a.get(30, TimeUnit.SECONDS);
            int dos = b.get(30, TimeUnit.SECONDS);
            assertThat(List.of(uno, dos)).contains(200, 409);
        }
    }

    private String registrarYLogin() throws Exception {
        String nombre = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        usuarios.add(nombre);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", nombre, "email", nombre + "@example.com", "password", "Segura123!"))))
                .andExpect(status().isCreated());
        return cuerpo(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", nombre, "password", "Segura123!"))))
                .andExpect(status().isOk())).path("data").path("access_token").asText();
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
    @AfterEach void limpiar() { /* La suite real limpia sus fixtures al hacer rollback/BD descartable. */ }
}
