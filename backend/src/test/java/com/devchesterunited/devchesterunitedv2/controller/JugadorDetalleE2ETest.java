package com.devchesterunited.devchesterunitedv2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JugadorDetalleE2ETest {
    private static final UUID EQUIPO_ACTIVO = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPO_INACTIVO = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID JUGADOR_CON_ESTADISTICAS = UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID JUGADOR_SIN_ESTADISTICAS = UUID.fromString("42000000-0000-0000-0000-000000000002");
    private static final UUID JUGADOR_INACTIVO = UUID.fromString("42000000-0000-0000-0000-000000000003");
    private static final UUID JUGADOR_EQUIPO_INACTIVO = UUID.fromString("42000000-0000-0000-0000-000000000004");
    private static final UUID SYNC_ID = UUID.fromString("43000000-0000-0000-0000-000000000001");
    private static final UUID ESTADISTICAS_ID = UUID.fromString("44000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepararCatalogo() {
        jdbc.update("DELETE FROM jugador_estadisticas");
        jdbc.update("DELETE FROM estadisticas_sync_staging");
        jdbc.update("DELETE FROM estadisticas_sync");
        jdbc.update("DELETE FROM jugadores");
        jdbc.update("UPDATE equipos SET is_active = TRUE");

        insertarEquipo(EQUIPO_ACTIVO, "Equipo detalle activo", "Liga detalle", true);
        insertarEquipo(EQUIPO_INACTIVO, "Equipo detalle inactivo", "Liga detalle", false);
        insertarJugador(JUGADOR_CON_ESTADISTICAS, "Jugador Con Estadisticas", EQUIPO_ACTIVO, true, 9);
        insertarJugador(JUGADOR_SIN_ESTADISTICAS, "Jugador Sin Estadisticas", EQUIPO_ACTIVO, true, 10);
        insertarJugador(JUGADOR_INACTIVO, "Jugador Inactivo", EQUIPO_ACTIVO, false, 11);
        insertarJugador(JUGADOR_EQUIPO_INACTIVO, "Jugador Equipo Inactivo", EQUIPO_INACTIVO, true, 12);
        insertarEstadisticas();
    }

    @Test
    void jugadorConEstadisticasDevuelveDetalleYMetricasPersistidas() throws Exception {
        JsonNode data = exito(JUGADOR_CON_ESTADISTICAS);
        JsonNode jugador = data.path("data");
        assertThat(jugador.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "name", "league", "team", "position", "jersey_number", "nationality", "statistics", "valuation");
        assertThat(jugador.path("valuation").isNull()).isTrue();
        assertThat(jugador.path("id").asText()).isEqualTo(idPublico(JUGADOR_CON_ESTADISTICAS));
        assertThat(jugador.path("name").asText()).isEqualTo("Jugador Con Estadisticas");
        assertThat(jugador.path("league").asText()).isEqualTo("Liga detalle");
        assertThat(jugador.path("team").asText()).isEqualTo("Equipo detalle activo");
        assertThat(jugador.path("statistics").fieldNames()).toIterable().containsExactlyInAnyOrder(
                "position", "minutes_played", "goals", "assists", "shots", "key_passes",
                "dribbles", "tackles", "rating", "yellow_cards", "red_cards");
        assertThat(jugador.path("statistics").path("position").asText()).isEqualTo("Forward");
        assertThat(jugador.path("statistics").path("minutes_played").intValue()).isEqualTo(1234);
        assertThat(jugador.path("statistics").path("goals").intValue()).isEqualTo(12);
        assertThat(jugador.path("statistics").path("assists").intValue()).isEqualTo(7);
        assertThat(jugador.path("statistics").path("shots").decimalValue()).isEqualByComparingTo(new BigDecimal("2.34"));
        assertThat(jugador.path("statistics").path("key_passes").decimalValue()).isEqualByComparingTo(new BigDecimal("1.23"));
        assertThat(jugador.path("statistics").path("dribbles").decimalValue()).isEqualByComparingTo(new BigDecimal("3.45"));
        assertThat(jugador.path("statistics").path("tackles").decimalValue()).isEqualByComparingTo(new BigDecimal("4.56"));
        assertThat(jugador.path("statistics").path("rating").decimalValue()).isEqualByComparingTo(new BigDecimal("7.89"));
        assertThat(jugador.path("statistics").path("yellow_cards").intValue()).isEqualTo(2);
        assertThat(jugador.path("statistics").path("red_cards").intValue()).isEqualTo(1);
        assertThat(jugador.path("statistics").has("id")).isFalse();
        assertThat(jugador.path("statistics").has("syncId")).isFalse();
        assertThat(jugador.path("statistics").has("createdAt")).isFalse();
        assertThat(jugador.path("statistics").has("updatedAt")).isFalse();
    }

    @Test
    void jugadorSinEstadisticasDevuelveDetalleConNull() throws Exception {
        JsonNode jugador = exito(JUGADOR_SIN_ESTADISTICAS).path("data");
        assertThat(jugador.path("id").asText()).isEqualTo(idPublico(JUGADOR_SIN_ESTADISTICAS));
        assertThat(jugador.path("statistics").isNull()).isTrue();
    }

    @Test
    void inexistenteInactivoYEquipoInactivoDevuelven404() throws Exception {
        for (UUID id : List.of(
                UUID.fromString("42000000-0000-0000-0000-000000000099"),
                JUGADOR_INACTIVO,
                JUGADOR_EQUIPO_INACTIVO)) {
            error(get("/api/v1/players/" + idPublico(id)), 404);
        }
    }

    @Test
    void listadoConservaSuContratoExistente() throws Exception {
        JsonNode response = json(mvc.perform(get("/api/v1/players"))
                .andExpect(status().isOk()));
        assertThat(response.fieldNames()).toIterable().containsExactlyInAnyOrder("status", "data");
        assertThat(response.path("status").asText()).isEqualTo("success");
        JsonNode data = response.path("data");
        assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrder("players", "pagination");
        assertThat(data.path("players")).hasSize(2);
        for (JsonNode jugador : data.path("players")) {
            assertThat(jugador.fieldNames()).toIterable().containsExactlyInAnyOrder(
                    "id", "name", "league", "team", "position", "jersey_number", "nationality", "valuation");
            assertThat(jugador.path("valuation").isNull()).isTrue();
            assertThat(jugador.has("statistics")).isFalse();
        }
    }

    private JsonNode exito(UUID id) throws Exception {
        JsonNode response = json(mvc.perform(get("/api/v1/players/" + idPublico(id)))
                .andExpect(status().isOk()));
        assertThat(response.fieldNames()).toIterable().containsExactlyInAnyOrder("status", "data");
        assertThat(response.path("status").asText()).isEqualTo("success");
        return response;
    }

    private void error(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expectedStatus) throws Exception {
        JsonNode response = json(mvc.perform(request).andExpect(status().is(expectedStatus)));
        assertThat(response.path("status").asText()).isEqualTo("error");
        assertThat(response.path("message").asText()).isEqualTo("Recurso no encontrado.");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString());
    }

    private void insertarEquipo(UUID id, String nombre, String liga, boolean activo) {
        jdbc.update("""
                INSERT INTO equipos (id, name, league, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, nombre, liga, activo);
    }

    private void insertarJugador(UUID id, String nombre, UUID equipoId, boolean activo, int dorsal) {
        jdbc.update("""
                INSERT INTO jugadores (id, name, team_id, position, jersey_number, nationality,
                                       is_active, created_at, updated_at)
                VALUES (?, ?, ?, 'Forward', ?, 'Argentina', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, nombre, equipoId, dorsal, activo);
    }

    private void insertarEstadisticas() {
        jdbc.update("""
                INSERT INTO estadisticas_sync (id, estado, iniciada_at, finalizada_at)
                VALUES (?, 'PUBLICADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, SYNC_ID);
        jdbc.update("""
                INSERT INTO jugador_estadisticas
                    (id, jugador_id, posicion, minutos_jugados, goles, asistencias,
                     tiros, pases_clave, regates, tackles, valoracion,
                     tarjetas_amarillas, tarjetas_rojas, sync_id, created_at, updated_at)
                VALUES (?, ?, 'Forward', 1234, 12, 7, 2.34, 1.23, 3.45, 4.56, 7.89,
                        2, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ESTADISTICAS_ID, JUGADOR_CON_ESTADISTICAS, SYNC_ID);
    }

    private static String idPublico(UUID id) {
        return "ply_" + id;
    }
}
