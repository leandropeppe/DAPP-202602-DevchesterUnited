package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.adapter.FootballDataException;
import com.devchesterunited.devchesterunitedv2.adapter.impl.FootballDataAdapter;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.UsuarioEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogoSyncE2ETest extends PostgresTestSupport {
    private static final UUID USUARIO = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @MockBean FootballDataAdapter adapter;

    @BeforeEach
    void prepararUsuario() {
        jdbc.update("DELETE FROM jugadores WHERE external_id IS NOT NULL");
        jdbc.update("DELETE FROM equipos WHERE external_id IS NOT NULL");
        jdbc.update("DELETE FROM usuarios WHERE id = ?", USUARIO);
        usuarios.save(new UsuarioEntity(USUARIO, "synce2euser", "synce2e@example.com", "$2a$12$" + "a".repeat(53), AHORA, AHORA));
        reset(adapter);
    }

    @AfterEach
    void limpiarUsuario() {
        jdbc.update("DELETE FROM jugadores WHERE external_id IS NOT NULL");
        jdbc.update("DELETE FROM equipos WHERE external_id IS NOT NULL");
        jdbc.update("DELETE FROM usuarios WHERE id = ?", USUARIO);
    }

    @Test
    void jwtValidoPublicaSnapshotCompletoYDevuelveConteos() throws Exception {
        when(adapter.sincronizar()).thenReturn(snapshot(900000L, 910000L));
        mvc.perform(post("/api/v1/catalog/sync").header("X-API-KEY", apiKeyValida()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.teams_processed").value(96))
                .andExpect(jsonPath("$.data.players_processed").value(96));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class)).isEqualTo(96L);
        verify(adapter).sincronizar();
    }

    @Test
    void jwtAusenteInvalidoYBodyNoVacioSeRechazan() throws Exception {
        mvc.perform(post("/api/v1/catalog/sync")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/catalog/sync").with(SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject("usr_" + UUID.randomUUID())))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/catalog/sync").header("X-API-KEY", apiKeyValida())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adapter);
    }

    @Test
    void proveedorFallaDevuelve503YConservaSnapshotAnterior() throws Exception {
        when(adapter.sincronizar()).thenReturn(snapshot(920000L, 930000L));
        mvc.perform(post("/api/v1/catalog/sync").header("X-API-KEY", apiKeyValida()))
                .andExpect(status().isOk());
        long equiposAntes = jdbc.queryForObject("SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class);
        when(adapter.sincronizar()).thenThrow(new FootballDataException("fallo simulado"));
        mvc.perform(post("/api/v1/catalog/sync").header("X-API-KEY", apiKeyValida()))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class)).isEqualTo(equiposAntes);
    }

    @Test
    void errorInesperadoDevuelve500Generico() throws Exception {
        when(adapter.sincronizar()).thenThrow(new IllegalStateException("detalle interno"));
        mvc.perform(post("/api/v1/catalog/sync").header("X-API-KEY", apiKeyValida()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void consultaDeJugadoresNoLlamaAlAdapter() throws Exception {
        mvc.perform(get("/api/v1/players")).andExpect(status().isOk());
        verifyNoInteractions(adapter);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtUsuario() {
        return jwt().jwt(j -> j.subject("usr_" + USUARIO));
    }

    private String apiKeyValida() throws Exception {
        return mvc.perform(post("/api/v1/auth/apikey").with(jwtUsuario()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .replaceFirst("(?s).*\"api_key\":\"([^\"]+)\".*", "$1");
    }

    private static CatalogoSincronizado snapshot(long equipoBase, long jugadorBase) {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        var equipos = new ArrayList<Equipo>();
        var jugadores = new ArrayList<Jugador>();
        for (int i = 0; i < 96; i++) {
            int liga = i < 20 ? 0 : i < 40 ? 1 : i < 58 ? 2 : i < 78 ? 3 : 4;
            UUID equipoId = UUID.randomUUID();
            equipos.add(Equipo.importado(equipoId, equipoBase + i, "Equipo " + i, "E", "E", ligas.get(liga), null,
                    2026, null, null, AHORA));
            jugadores.add(Jugador.importado(UUID.randomUUID(), jugadorBase + i, "Jugador " + i + " Apellido", equipoId,
                    "Forward", null, "Argentina", AHORA));
        }
        return new CatalogoSincronizado(equipos, jugadores);
    }
}
