package com.devchesterunited.devchesterunitedv2.controller;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "football-data.api-key=")
@AutoConfigureMockMvc
class CatalogoSyncMissingCredentialE2ETest extends PostgresTestSupport {
    private static final UUID USUARIO = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID EQUIPO = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID JUGADOR = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final Instant AHORA = Instant.parse("2026-09-18T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired EquipoRepository equipos;
    @Autowired IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired JugadorRepository jugadores;
    @Autowired UsuarioRepository usuarios;

    @BeforeEach
    void prepararUsuarioYCatalogo() {
        limpiarFixturesPropios();

        usuarios.saveAndFlush(new UsuarioEntity(USUARIO, "missingcredentialuser",
                "missingcredential@example.com", "$2a$12$" + "a".repeat(53), AHORA, AHORA));
        EquipoEntity equipoPersistido = equipos.saveAndFlush(EquipoEntity.builder().id(EQUIPO)
                .nombre("Equipo Persistido").nombreCorto("EP").sigla("EP").liga("LaLiga")
                .escudoUrl(null).activo(true).temporadaExternaId(2518L)
                .inicioTemporada(LocalDate.of(2026, 8, 16)).finTemporada(LocalDate.of(2027, 5, 30))
                .creadoEn(AHORA).actualizadoEn(AHORA).build());
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipoPersistido, ProveedorIdentidadExterna.FOOTBALL_DATA, 700001L));
        JugadorEntity jugador = new JugadorEntity(JUGADOR, "Jugador Persistido", EQUIPO,
                "Forward", null, "Argentina", true);
        jugador.conservarAuditoria(AHORA, AHORA);
        jugadores.saveAndFlush(jugador);
    }

    @AfterEach
    void limpiarDespuesDeCadaTest() {
        limpiarFixturesPropios();
    }

    private void limpiarFixturesPropios() {
        Set<UUID> jugadoresPropios = new HashSet<>();
        jugadores.findById(JUGADOR).ifPresent(jugador -> jugadoresPropios.add(jugador.getId()));
        jugadores.deleteAllById(jugadoresPropios);
        jugadores.flush();

        Set<UUID> equiposPropios = new HashSet<>();
        equipos.findById(EQUIPO).ifPresent(equipo -> equiposPropios.add(equipo.getId()));
        identidadesEquipos.findByProveedorAndExternalId(ProveedorIdentidadExterna.FOOTBALL_DATA, 700001L)
                .ifPresent(identidad -> equiposPropios.add(identidad.getEquipo().getId()));
        equipos.deleteAllById(equiposPropios);
        equipos.flush();

        usuarios.findById(USUARIO).ifPresent(usuarios::delete);
        usuarios.flush();
    }

    @Test
    void apiKeyInternaSinCredencialExternaDevuelve503SinSecretosYConservaCatalogo() throws Exception {
        String apiKey = apiKeyValida();

        mvc.perform(post("/api/v1/catalog/sync").header("X-API-KEY", apiKey))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("No fue posible sincronizar el catalogo con el proveedor externo"));

        assertThat(equipos.findById(EQUIPO)).get().satisfies(equipo -> {
            assertThat(equipo.isActivo()).isTrue();
        });
        assertThat(identidadesEquipos.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.FOOTBALL_DATA, 700001L)).isPresent();
        assertThat(jugadores.findById(JUGADOR)).get().satisfies(jugador -> {
            assertThat(jugador.isActivo()).isTrue();
        });
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtUsuario() {
        return jwt().jwt(j -> j.subject("usr_" + USUARIO));
    }

    private String apiKeyValida() throws Exception {
        String json = mvc.perform(post("/api/v1/auth/apikey").with(jwtUsuario()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(json)
                .path("data")
                .path("api_key")
                .asText();
    }
}
