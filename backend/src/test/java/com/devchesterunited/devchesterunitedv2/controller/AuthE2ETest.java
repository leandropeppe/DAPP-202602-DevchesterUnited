package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato HTTP contra la aplicación real, sin controllers ni servicios simulados.
 * Fixtures únicos en la BD descartable de Testcontainers, sin depender del orden.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthE2ETest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final List<String> registrados = new ArrayList<>();
    private static final String CLAVE = "Segura123!";

    @Test
    void registro201YLogin200RespetanEsquemasYMensajesEnEspanol() throws Exception {
        String nombre = identidad();
        JsonNode registro = cuerpo(enviar("register", Map.of("username", " " + nombre.toUpperCase(java.util.Locale.ROOT) + " ",
                "email", " " + nombre.toUpperCase(java.util.Locale.ROOT) + "@EXAMPLE.COM ", "password", CLAVE))
                .andExpect(status().isCreated()));
        registrados.add(nombre);
        campos(registro, "status", "message", "data");
        assertThat(registro.path("status").asText()).isEqualTo("success");
        assertThat(registro.path("message").asText()).isEqualTo("Usuario registrado correctamente");
        JsonNode usuario = registro.path("data");
        campos(usuario, "id", "username", "email", "created_at");
        assertThat(usuario.path("id").asText()).matches("usr_[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
        assertThat(usuario.path("username").asText()).isEqualTo(nombre);
        assertThat(usuario.path("email").asText()).isEqualTo(nombre + "@example.com");
        assertThatCode(() -> Instant.parse(usuario.path("created_at").asText())).doesNotThrowAnyException();
        JsonNode login = cuerpo(enviar("login", Map.of("username", " " + nombre.toUpperCase(java.util.Locale.ROOT) + " ", "password", CLAVE))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store"))));
        campos(login, "status", "message", "data");
        assertThat(login.path("status").asText()).isEqualTo("success");
        assertThat(login.path("message").asText()).isEqualTo("Autenticación exitosa");
        JsonNode datos = login.path("data");
        campos(datos, "token_type", "access_token", "expires_in");
        assertThat(datos.path("token_type").asText()).isEqualTo("Bearer");
        assertThat(datos.path("access_token").asText()).matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
        assertThat(datos.path("expires_in").isIntegralNumber()).isTrue();
        assertThat(datos.path("expires_in").asInt()).isEqualTo(3600);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicadosNormalizadosDevuelven409(boolean nombreDuplicado) throws Exception {
        String nombre = identidad();
        registrar(nombre);
        error(enviar("register", Map.of("username", nombreDuplicado ? nombre.toUpperCase(java.util.Locale.ROOT) : identidad(),
                "email", nombreDuplicado ? identidad() + "@example.com" : " " + nombre.toUpperCase(java.util.Locale.ROOT) + "@EXAMPLE.COM ",
                "password", CLAVE)), 409, "El nombre de usuario o el correo electrónico ya está registrado.");
    }

    @Test
    void login401NoRevelaSiExisteUsuarioNiTransformaContrasena() throws Exception {
        String nombre = identidad();
        registrar(nombre);
        for (var credenciales : List.of(Map.of("username", nombre, "password", "x"),
                Map.of("username", identidad(), "password", "x"), Map.of("username", nombre, "password", " " + CLAVE))) {
            error(enviar("login", credenciales), 401, "Nombre de usuario o contraseña incorrectos");
        }
    }

    @ParameterizedTest
    @MethodSource("invalidos")
    void entradasInvalidasDevuelven400ConDetalleEnEspanol(String endpoint, String campo, String valor) throws Exception {
        var entrada = new java.util.HashMap<String, Object>();
        entrada.put("username", identidad());
        entrada.put("password", CLAVE);
        if (endpoint.equals("register")) entrada.put("email", identidad() + "@example.com");
        if (valor == null) entrada.remove(campo); else entrada.put(campo, valor);
        JsonNode respuesta = error(enviar(endpoint, entrada), 400, "Error de validación");
        assertThat(respuesta.path("errors").isArray()).isTrue();
        List<JsonNode> detalles = new ArrayList<>();
        respuesta.path("errors").forEach(detalles::add);
        assertThat(detalles).anySatisfy(detalle -> {
            campos(detalle, "field", "issue");
            assertThat(detalle.path("field").asText()).isEqualTo(campo);
            assertThat(detalle.path("issue").asText())
                    .containsPattern("(?is)(?:debe|inválid|válid|obligatori|contraseña|vacío|caracteres|formato|longitud)");
        });
    }

    static Stream<Arguments> invalidos() {
        var casos = new ArrayList<Arguments>();
        for (String endpoint : List.of("register", "login")) {
            for (String usuario : new String[]{null, "", "ab", "a".repeat(31), "no_valido", "ábc"})
                casos.add(Arguments.of(endpoint, "username", usuario));
            for (String clave : new String[]{null, "", "Aa1" + "x".repeat(70), "Aa1" + "é".repeat(35)})
                casos.add(Arguments.of(endpoint, "password", clave));
        }
        for (String correo : new String[]{null, "", "sin-arroba", "a".repeat(243) + "@example.com"})
            casos.add(Arguments.of("register", "email", correo));
        for (String clave : List.of("Aa12345", "minuscula123", "MAYUSCULA123", "SinNumeros", " Segura123!", "Segura123! ", "Segura\n123!"))
            casos.add(Arguments.of("register", "password", clave));
        return casos.stream();
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "login"})
    void rechazaJsonDesconocidoMalformadoYNulo(String endpoint) throws Exception {
        String base = mapper.writeValueAsString(endpoint.equals("register")
                ? Map.of("username", identidad(), "email", identidad() + "@example.com", "password", CLAVE)
                : Map.of("username", identidad(), "password", CLAVE));
        for (String json : List.of(base.substring(0, base.length() - 1) + ",\"desconocido\":\"NO_FILTRAR\"}", "{", "null")) {
            error(mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON).content(json)),
                    400, "La solicitud no es válida.");
        }
    }

    private void registrar(String nombre) throws Exception {
        enviar("register", Map.of("username", nombre, "email", nombre + "@example.com", "password", CLAVE))
                .andExpect(status().isCreated());
        registrados.add(nombre);
    }

    @AfterEach
    void limpiarFixtures() {
        for (String nombre : registrados) jdbc.update("DELETE FROM usuarios WHERE username = ?", nombre);
    }

    private ResultActions enviar(String endpoint, Object entrada) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(entrada)));
    }

    private JsonNode error(ResultActions resultado, int codigo, String mensaje) throws Exception {
        JsonNode json = cuerpo(resultado.andExpect(status().is(codigo)));
        assertThat(json.path("status").asText()).isEqualTo("error");
        assertThat(json.path("message").asText()).isEqualTo(mensaje);
        if (json.has("errors")) {
            campos(json, "status", "message", "errors");
            assertThat(json.path("errors").isArray()).isTrue();
            for (JsonNode detalle : json.path("errors")) {
                campos(detalle, "field", "issue");
                assertThat(detalle.path("field").isTextual()).isTrue();
                assertThat(detalle.path("issue").isTextual()).isTrue();
            }
        } else campos(json, "status", "message");
        return json;
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        String json = resultado.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).doesNotContain(CLAVE, "password_hash", "NO_FILTRAR", "stackTrace", "SELECT ");
        return mapper.readTree(json);
    }

    private static void campos(JsonNode json, String... esperados) {
        var nombres = new ArrayList<String>();
        json.fieldNames().forEachRemaining(nombres::add);
        assertThat(nombres).containsExactlyInAnyOrder(esperados);
    }

    private static String identidad() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }
}
