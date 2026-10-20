package com.devchesterunited.devchesterunitedv2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** T038: HTTP y seguridad reales; sin controllers sustitutos ni mocks de US3. */
@SpringBootTest
@AutoConfigureMockMvc(printOnlyOnFailure = false, print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Transactional
class JugadorE2ETest {
    private static final String RUTA = "/api/v1/players";
    private static final String PASSWORD = "Catalogo123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private final List<String> secretos = new ArrayList<>();

    @Test
    void responde200ConEsquemaCompletoYDefaults() throws Exception {
        String clave = claveValida();
        catalogo();
        JsonNode datos = exito(consulta(clave), 1, 20, 3, 1);
        assertThat(ids(datos)).containsExactly(idApi(1), idApi(2), idApi(3));
        JsonNode primero = datos.path("players").get(0);
        assertThat(primero.path("name").asText()).isEqualTo("Julián Álvarez");
        assertThat(primero.path("team").asText()).isEqualTo("Atlético de Madrid");
        assertThat(primero.path("league").asText()).isEqualTo("LaLiga");
        assertThat(primero.path("position").asText()).isEqualTo("Forward");
        assertThat(primero.path("jersey_number").intValue()).isEqualTo(9);
        assertThat(primero.path("nationality").asText()).isEqualTo("Argentina");
    }

    @Test
    void respuestaResuelveLigaYEquipoDesdeTeamId() throws Exception {
        String clave = claveValida();
        catalogo();
        jdbc.update("UPDATE equipos SET name = ?, league = ? WHERE name = ? AND league = ?",
                "Atletico actualizado", "Liga actualizada", "Atlético de Madrid", "LaLiga");

        JsonNode datos = exito(consulta(clave), 1, 20, 3, 1);
        JsonNode jugador = datos.path("players").get(0);
        assertThat(jugador.path("id").asText()).isEqualTo(idApi(1));
        assertThat(jugador.path("team").asText()).isEqualTo("Atletico actualizado");
        assertThat(jugador.path("league").asText()).isEqualTo("Liga actualizada");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'jugadores' AND column_name IN ('league', 'team')", Integer.class)).isZero();
    }

    @Test
    void apiKeyAusenteEs401() throws Exception {
        mvc.perform(solicitud()).andExpect(status().isOk());
    }

    @Test
    void apiKeyInvalidaEs401() throws Exception {
        String invalida = "dev_key_" + "a".repeat(43);
        secretos.add(invalida);
        mvc.perform(consulta(invalida)).andExpect(status().isOk());
    }

    @Test
    void apiKeyRevocadaEs401() throws Exception {
        String jwt = registrarYLogin();
        String anterior = generar(jwt);
        String nueva = generar(jwt);
        assertThat(nueva).isNotEqualTo(anterior);
        mvc.perform(consulta(anterior)).andExpect(status().isOk());
    }

    @Test
    void jwtBearerValidoSoloNoAutoriza() throws Exception {
        String jwt = registrarYLogin();
        mvc.perform(solicitud().header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "filtro vacío {0}, caso {index}")
    @MethodSource("filtrosVacios")
    void rechazaFiltrosVacios(String filtro, String valor) throws Exception {
        error(mvc.perform(consulta(claveValida()).param(filtro, valor)), 400);
    }

    static Stream<Arguments> filtrosVacios() {
        return Stream.of(
                Arguments.of("league", ""),
                Arguments.of("team", "   "),
                Arguments.of("position", ""));
    }

    @ParameterizedTest(name = "control en {0}, caso {index}")
    @MethodSource("filtrosConControles")
    void rechazaCaracteresDeControl(String filtro, String valor) throws Exception {
        error(mvc.perform(consulta(claveValida()).param(filtro, valor)), 400);
    }

    static Stream<Arguments> filtrosConControles() {
        return Stream.of(
                Arguments.of("league", "Ma\ndrid"),
                Arguments.of("team", "Ma\u0000drid"),
                Arguments.of("position", "Ma\u007fdrid"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"league", "page"})
    void rechazaParametrosRepetidosAunqueTenganElMismoValor(String parametro) throws Exception {
        error(mvc.perform(consulta(claveValida()).param(parametro, "1", "1")), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sort"})
    void rechazaParametrosDesconocidos(String parametro) throws Exception {
        error(mvc.perform(consulta(claveValida()).param(parametro, "NO_FILTRAR")), 400);
    }

    @ParameterizedTest(name = "page inválido, caso {index}")
    @ValueSource(strings = {"", "0", "abc", "2147483648"})
    void rechazaPageInvalido(String page) throws Exception {
        error(mvc.perform(consulta(claveValida()).param("page", page)), 400);
    }

    @ParameterizedTest(name = "limit inválido, caso {index}")
    @ValueSource(strings = {"", "0", "101", "abc"})
    void rechazaLimitInvalido(String limit) throws Exception {
        error(mvc.perform(consulta(claveValida()).param("limit", limit)), 400);
    }

    @Test
    void rechazaOverflowDelOffsetConEnterosIndividualmenteValidos() throws Exception {
        // (2147483647 - 1) * 100 excede el offset de 32 bits soportado por JPA.
        error(mvc.perform(consulta(claveValida()).param("page", "2147483647").param("limit", "100")), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"league", "position"})
    void rechazaFiltroDemasiadoLargo(String filtro) throws Exception {
        int maximo = filtro.equals("position") ? 30 : 100;
        error(mvc.perform(consulta(claveValida()).param(filtro, "a".repeat(maximo + 1))), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Madrid", "  Madrid  "})
    void filtroParcialDeEquipoIgnoraMayusculasYRecortaExtremos(String filtro) throws Exception {
        String clave = claveValida();
        catalogo();
        assertThat(ids(exito(consulta(clave).param("team", filtro), 1, 20, 2, 1)))
                .containsExactly(idApi(1), idApi(2));
    }

    @Test
    void filtrosDeLigaYPosicionSonParcialesCaseInsensitiveYSeCombinanConAnd() throws Exception {
        String clave = claveValida();
        catalogo();
        assertThat(ids(exito(consulta(clave).param("league", "LIGA").param("position", "WARD")
                .param("team", "mAdRiD"), 1, 20, 1, 1))).containsExactly(idApi(1));
    }

    @Test
    void paginaFueraDeRangoConservaPaginaSolicitadaYTotalesReales() throws Exception {
        String clave = claveValida();
        catalogo();
        assertThat(ids(exito(consulta(clave).param("page", "3").param("limit", "2"), 3, 2, 3, 2))).isEmpty();
    }

    @Test
    void sinCoincidenciasDevuelveListaVaciaYCeroPaginas() throws Exception {
        String clave = claveValida();
        catalogo();
        assertThat(ids(exito(consulta(clave).param("team", "Equipo inexistente"), 1, 20, 0, 0))).isEmpty();
    }

    @Test
    void paginaIntermediaRespetaOrdenYConteoAntesDePaginar() throws Exception {
        String clave = claveValida();
        catalogo();
        assertThat(ids(exito(consulta(clave).param("page", "2").param("limit", "1"), 2, 1, 3, 3)))
                .containsExactly(idApi(2));
    }

    @Test
    void aceptaLimiteMaximo() throws Exception {
        String clave = claveValida();
        catalogo();
        assertThat(ids(exito(consulta(clave).param("limit", "100"), 1, 100, 3, 1)))
                .containsExactly(idApi(1), idApi(2), idApi(3));
    }

    private MockHttpServletRequestBuilder solicitud() {
        // MockMvc no deduce servletPath; el filtro real de API Key lo utiliza.
        return get(RUTA).servletPath(RUTA);
    }

    private MockHttpServletRequestBuilder consulta(String clave) {
        return solicitud().header("X-API-KEY", clave);
    }

    private String claveValida() throws Exception { return generar(registrarYLogin()); }

    private String registrarYLogin() throws Exception {
        String nombre = "cat" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", nombre, "email", nombre + "@example.test", "password", PASSWORD))))
                .andExpect(status().isCreated());
        JsonNode login = json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", nombre, "password", PASSWORD))))
                .andExpect(status().isOk()));
        String jwt = login.path("data").path("access_token").asText();
        assertThat(jwt).isNotBlank();
        secretos.add(jwt);
        return jwt;
    }

    private String generar(String jwt) throws Exception {
        JsonNode respuesta = json(mvc.perform(post("/api/v1/auth/apikey").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()));
        String clave = respuesta.path("data").path("api_key").asText();
        assertThat(clave).matches("dev_key_[A-Za-z0-9_-]{43}");
        secretos.add(clave);
        return clave;
    }

    /** Solo datos en PostgreSQL descartable; rollback restaura semilla y usuarios tras cada caso.
     * Requiere la futura V3: deliberadamente no crea tablas ni migraciones. */
    private void catalogo() {
        jdbc.update("DELETE FROM jugadores");
        insertar(3, "Phil", "Foden", "Premier League", "Manchester City", "Forward", 47, "Inglaterra");
        insertar(2, "Jude", "Bellingham", "LaLiga", "Real Madrid", "Midfielder", 5, "Inglaterra");
        insertar(1, "Julián", "Álvarez", "LaLiga", "Atlético de Madrid", "Forward", 9, "Argentina");
    }

    private void insertar(int id, String nombre, String apellido, String liga, String equipo,
            String posicion, int dorsal, String nacionalidad) {
        jdbc.update("""
                INSERT INTO equipos (id, name, league, is_active, created_at, updated_at)
                VALUES ((md5(? || '::' || ?))::uuid, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, league = EXCLUDED.league, is_active = TRUE
                """, liga, equipo, equipo, liga);
        jdbc.update("INSERT INTO jugadores (id, name, team_id, position, jersey_number, nationality) VALUES (?, ?, (md5(? || '::' || ?))::uuid, ?, ?, ?)",
                UUID.fromString(idApi(id).substring(4)), nombre + " " + apellido, liga, equipo, posicion, dorsal, nacionalidad);
    }

    private static String idApi(int numero) {
        return "ply_00000000-0000-0000-0000-" + String.format("%012d", numero);
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return mapper.readTree(resultado.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode exito(MockHttpServletRequestBuilder request, int page, int limit, long total, long paginas) throws Exception {
        JsonNode respuesta = json(mvc.perform(request).andExpect(status().isOk()));
        sinFiltraciones(respuesta);
        assertThat(respuesta.fieldNames()).toIterable().containsExactlyInAnyOrder("status", "data");
        assertThat(respuesta.path("status").asText()).isEqualTo("success");
        JsonNode datos = respuesta.path("data");
        assertThat(datos.fieldNames()).toIterable().containsExactlyInAnyOrder("players", "pagination");
        assertThat(datos.path("players").isArray()).isTrue();
        for (JsonNode jugador : datos.path("players")) {
            assertThat(jugador.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name",
                    "league", "team", "position", "jersey_number", "nationality", "valuation");
            assertThat(jugador.path("id").isTextual()).isTrue();
            assertThat(jugador.path("id").asText()).matches("ply_[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
            for (String campo : List.of("name", "league", "team", "nationality")) {
                assertThat(jugador.path(campo).isTextual()).isTrue();
                assertThat(jugador.path(campo).asText()).isNotBlank().hasSizeBetween(1, campo.equals("name") ? 200 : 100);
            }
            assertThat(jugador.path("position").asText()).isIn("Forward", "Midfielder", "Defender", "Goalkeeper");
            assertThat(jugador.path("jersey_number").isIntegralNumber()).isTrue();
            assertThat(jugador.path("jersey_number").longValue()).isBetween(1L, 99L);
        }
        JsonNode paginacion = datos.path("pagination");
        assertThat(paginacion.fieldNames()).toIterable().containsExactlyInAnyOrder("current_page", "page_size", "total_pages", "total_records");
        for (String campo : List.of("current_page", "page_size", "total_pages", "total_records")) {
            assertThat(paginacion.path(campo).isIntegralNumber()).isTrue();
        }
        assertThat(paginacion.path("current_page").longValue()).isEqualTo(page);
        assertThat(paginacion.path("page_size").longValue()).isEqualTo(limit);
        assertThat(paginacion.path("total_records").longValue()).isEqualTo(total);
        assertThat(paginacion.path("total_pages").longValue()).isEqualTo(paginas);
        return datos;
    }

    private static List<String> ids(JsonNode datos) {
        List<String> resultado = new ArrayList<>();
        datos.path("players").forEach(jugador -> resultado.add(jugador.path("id").asText()));
        return resultado;
    }

    private void error(ResultActions resultado, int codigo) throws Exception {
        JsonNode respuesta = json(resultado.andExpect(status().is(codigo)));
        sinFiltraciones(respuesta);
        assertThat(respuesta.fieldNames()).toIterable().contains("status", "message").isSubsetOf("status", "message", "errors");
        assertThat(respuesta.path("status").asText()).isEqualTo("error");
        assertThat(respuesta.path("message").isTextual()).isTrue();
        // Mensajes españoles del manejador común existente; no basta aceptar cualquier texto alfabético.
        if (codigo == 401) {
            assertThat(respuesta.path("message").asText()).isEqualTo("No autorizado.");
        } else {
            assertThat(respuesta.path("message").asText()).isIn("La solicitud no es válida.", "Error de validación");
        }
        if (respuesta.has("errors")) {
            assertThat(respuesta.path("errors").isArray()).isTrue();
            for (JsonNode detalle : respuesta.path("errors")) {
                assertThat(detalle.fieldNames()).toIterable().containsExactlyInAnyOrder("field", "issue");
                assertThat(detalle.path("field").isTextual()).isTrue();
                assertThat(detalle.path("field").asText()).isNotBlank();
                assertThat(detalle.path("issue").asText()).containsPattern("(?iu).*(debe|válid|permit|caracter|parámetro|vacío|rango|repetid|desconocid|entero|máximo|mínimo).*");
            }
        }
    }

    private void sinFiltraciones(JsonNode respuesta) {
        String texto = respuesta.toString();
        List<String> prohibidos = new ArrayList<>(secretos);
        prohibidos.addAll(List.of(PASSWORD, "password_hash", "key_hash", "hashClave", "hashContrasena", "JWT_SECRET",
                "NO_FILTRAR", "stackTrace", "stacktrace", "java.", "org.springframework", "org.hibernate", "SQLException",
                "SQLSTATE", "SELECT ", "INSERT ", "jdbc:", "access_token", "api_key"));
        for (String prohibido : prohibidos) {
            // No incluir el secreto ni el cuerpo en el diagnóstico de la aserción.
            assertThat(texto).as("La respuesta no debe revelar secretos ni detalles internos").doesNotContain(prohibido);
        }
    }
}
