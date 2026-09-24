package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.configuration.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import static org.assertj.core.api.Assertions.*;

class AutenticacionDTOTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private final ObjectMapper mapper = mapper();

    private static ObjectMapper mapper() {
        var builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().strictJson().customize(builder);
        return builder.build();
    }

    @AfterAll
    static void cerrar() { FACTORY.close(); }

    @Test
    void normalizaAntesDeValidarYSoloIdentidades() throws Exception {
        var registro = mapper.readValue("""
                {"username":"  JUGADOR1  ","email":"  JUGADOR@EXAMPLE.COM  ","password":"Segura 123!"}
                """, RegistroRequestDTO.class);
        assertThat(registro.nombreUsuario()).isEqualTo("jugador1");
        assertThat(registro.correo()).isEqualTo("jugador@example.com");
        assertThat(registro.contrasena()).isEqualTo("Segura 123!");
        assertThat(VALIDATOR.validate(registro)).isEmpty();
        var login = mapper.readValue("""
                {"username":"  JUGADOR1  ","password":" x "}
                """, LoginRequestDTO.class);
        assertThat(login.nombreUsuario()).isEqualTo("jugador1");
        assertThat(login.contrasena()).isEqualTo(" x ");
        assertThat(VALIDATOR.validate(login)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "no_valido", "ábc", "abcdefghijklmnopqrstuvwxyz12345"})
    void validaNombreEnAmbosRequests(String nombre) {
        assertThat(VALIDATOR.validate(new RegistroRequestDTO(nombre, "a@example.com", "Segura123!"))).isNotEmpty();
        assertThat(VALIDATOR.validate(new LoginRequestDTO(nombre, "x"))).isNotEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "sin-arroba", "a@@example.com", "a b@example.com"})
    void correoObligatorioYValido(String correo) {
        assertThat(VALIDATOR.validate(new RegistroRequestDTO("jugador", correo, "Segura123!"))).isNotEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Aa12345", "minuscula123", "MAYUSCULA123", "SinNumeros",
            " Segura123!", "Segura123! ", "Segura\n123!", "Segura\u007f123!", "\u00a0Segura123!"})
    void registroRechazaContrasenasInvalidas(String clave) {
        var errores = VALIDATOR.validate(new RegistroRequestDTO("jugador", "a@example.com", clave));
        assertThat(errores).isNotEmpty().allSatisfy(error -> {
            assertThat(error.getPropertyPath()).hasToString("contrasena");
            assertThat(error.getMessage()).contains("contraseña");
        });
    }

    @Test
    void limitesExactosDeCaracteresYBytesSinTruncamiento() {
        for (String clave : List.of("Aa123456", "Aa1" + "x".repeat(69), "Aa12" + "é".repeat(34))) {
            assertThat(VALIDATOR.validate(new RegistroRequestDTO("abc", "a@example.com", clave))).isEmpty();
        }
        for (String clave : List.of("Aa1" + "x".repeat(70), "Aa1" + "é".repeat(35))) {
            var registro = new RegistroRequestDTO("abc", "a@example.com", clave);
            var login = new LoginRequestDTO("abc", clave);
            assertThat(registro.contrasena()).isEqualTo(clave);
            assertThat(login.contrasena()).isEqualTo(clave);
            assertThat(VALIDATOR.validate(registro)).isNotEmpty();
            assertThat(VALIDATOR.validate(login)).isNotEmpty();
        }
        for (String clave : List.of("x", " ", "\n", "x".repeat(72), "é".repeat(36))) {
            assertThat(VALIDATOR.validate(new LoginRequestDTO("abc", clave))).isEmpty();
        }
        assertThat(VALIDATOR.validate(new LoginRequestDTO("abc", null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new LoginRequestDTO("abc", ""))).isNotEmpty();
        String correo254 = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".com";
        assertThat(correo254).hasSize(254);
        assertThat(VALIDATOR.validate(new RegistroRequestDTO("a".repeat(30), correo254, "Segura123!"))).isEmpty();
        assertThat(VALIDATOR.validate(new RegistroRequestDTO("abc", correo254 + "a", "Segura123!"))).isNotEmpty();
    }

    @Test
    void rechazaCamposDesconocidosYTiposQueNoSeanTexto() throws Exception {
        for (Class<?> tipo : List.of(RegistroRequestDTO.class, LoginRequestDTO.class)) {
            var entrada = new java.util.HashMap<String, Object>(Map.of("username", "jugador", "password", "Segura123!"));
            if (tipo == RegistroRequestDTO.class) entrada.put("email", "a@example.com");
            entrada.put("extra", "valor");
            String desconocido = mapper.writeValueAsString(entrada);
            assertThatThrownBy(() -> mapper.readValue(desconocido, tipo)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
            entrada.remove("extra");
            for (String campo : List.copyOf(entrada.keySet())) {
                Object original = entrada.get(campo);
                for (Object invalido : List.of(123, true, List.of("x"), Map.of("x", "y"))) {
                    entrada.put(campo, invalido);
                    String json = mapper.writeValueAsString(entrada);
                    assertThatThrownBy(() -> mapper.readValue(json, tipo)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
                }
                entrada.put(campo, original);
            }
        }
    }

    @Test
    void respuestasConNombresExactosYSinSecretosEnLogs() throws Exception {
        String clave = "Segura123!";
        var registro = new RegistroRequestDTO("jugador", "a@example.com", clave);
        var login = new LoginRequestDTO("jugador", clave);
        assertThat(registro.toString()).doesNotContain(clave);
        assertThat(login.toString()).doesNotContain(clave);
        assertThat(mapper.writeValueAsString(registro)).doesNotContain("password", "contrasena", clave);
        assertThat(mapper.writeValueAsString(login)).doesNotContain("password", "contrasena", clave);
        var usuario = mapper.valueToTree(new UsuarioResponseDTO("usr_00000000-0000-0000-0000-000000000001",
                "jugador", "a@example.com", Instant.parse("2026-09-01T12:00:00Z")));
        assertThat(usuario.size()).isEqualTo(4);
        assertThat(usuario.has("id") && usuario.has("username") && usuario.has("email") && usuario.has("created_at")).isTrue();
        var respuesta = new LoginResponseDTO("token-ficticio");
        assertThat(respuesta.toString()).doesNotContain("token-ficticio");
        var json = mapper.valueToTree(respuesta);
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.path("access_token").asText()).isEqualTo(respuesta.getAccessToken());
        assertThat(json.path("token_type").asText()).isEqualTo("Bearer");
        assertThat(json.path("expires_in").asInt()).isEqualTo(3600);
    }
}
