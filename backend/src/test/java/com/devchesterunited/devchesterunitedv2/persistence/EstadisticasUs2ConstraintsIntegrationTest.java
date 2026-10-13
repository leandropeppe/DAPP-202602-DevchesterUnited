package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncStagingEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EstadisticasUs2ConstraintsIntegrationTest extends PostgresTestSupport {
    @Autowired private EquipoRepository equipos;
    @Autowired private JugadorRepository jugadores;
    @Autowired private EstadisticasJugadorRepository estadisticas;
    @Autowired private EstadisticasSyncRepository sincronizaciones;
    @Autowired private EstadisticasSyncStagingRepository staging;
    @Autowired private IdentidadExternaJugadorRepository identidadesJugadores;
    @Autowired private IdentidadExternaEquipoRepository identidadesEquipos;

    private EquipoEntity equipo;
    private JugadorEntity jugador;
    private EstadisticasSyncEntity sync;

    @BeforeEach
    void preparar() {
        Instant ahora = Instant.now();
        equipo = equipos.saveAndFlush(EquipoEntity.builder().id(UUID.randomUUID())
                .nombre("Equipo restricciones US2").liga("Premier League").activo(true)
                .creadoEn(ahora).actualizadoEn(ahora).build());
        jugador = jugadores.saveAndFlush(new JugadorEntity(UUID.randomUUID(),
                "Jugador restricciones US2", equipo.getId(), "Forward", null, "Argentina", true));
        sync = sincronizaciones.saveAndFlush(new EstadisticasSyncEntity(UUID.randomUUID(),
                EstadoEstadisticasSync.PUBLICADA, ahora));
    }

    @Test
    void impideDosEstadisticasParaElMismoJugador() {
        Instant ahora = Instant.now();
        estadisticas.saveAndFlush(new EstadisticasJugadorEntity(UUID.randomUUID(), jugador.getId(),
                null, null, null, null, null, null, null, null, null, null, null,
                sync.getId(), ahora, ahora));

        assertThatThrownBy(() -> estadisticas.saveAndFlush(new EstadisticasJugadorEntity(
                UUID.randomUUID(), jugador.getId(), null, null, null, null, null, null,
                null, null, null, null, null, sync.getId(), ahora, ahora)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideRepetirExternalIdWhoscoredParaJugadoresDiferentes() {
        JugadorEntity otro = nuevoJugador("Otro jugador externo");
        identidadesJugadores.saveAndFlush(new IdentidadExternaJugadorEntity(UUID.randomUUID(),
                jugador, ProveedorIdentidadExterna.WHOSCORED, 99101L));

        assertThatThrownBy(() -> identidadesJugadores.saveAndFlush(new IdentidadExternaJugadorEntity(
                UUID.randomUUID(), otro, ProveedorIdentidadExterna.WHOSCORED, 99101L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideDosIdentidadesWhoscoredParaElMismoJugador() {
        identidadesJugadores.saveAndFlush(new IdentidadExternaJugadorEntity(UUID.randomUUID(),
                jugador, ProveedorIdentidadExterna.WHOSCORED, 99102L));

        assertThatThrownBy(() -> identidadesJugadores.saveAndFlush(new IdentidadExternaJugadorEntity(
                UUID.randomUUID(), jugador, ProveedorIdentidadExterna.WHOSCORED, 99103L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideRepetirExternalIdWhoscoredParaEquiposDiferentes() {
        EquipoEntity otro = nuevoEquipo("Otro equipo externo");
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(UUID.randomUUID(),
                equipo, ProveedorIdentidadExterna.WHOSCORED, 99201L));

        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), otro, ProveedorIdentidadExterna.WHOSCORED, 99201L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideDosIdentidadesWhoscoredParaElMismoEquipo() {
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(UUID.randomUUID(),
                equipo, ProveedorIdentidadExterna.WHOSCORED, 99202L));

        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.WHOSCORED, 99203L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideRepetirJugadorPorSyncCompeticionYProveedor() {
        staging.saveAndFlush(new EstadisticasSyncStagingEntity(UUID.randomUUID(), sync.getId(),
                "PREMIER_LEAGUE", 99301L, "Jugador staging"));

        assertThatThrownBy(() -> staging.saveAndFlush(new EstadisticasSyncStagingEntity(
                UUID.randomUUID(), sync.getId(), "PREMIER_LEAGUE", 99301L, "Jugador staging")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JugadorEntity nuevoJugador(String nombre) {
        return jugadores.saveAndFlush(new JugadorEntity(UUID.randomUUID(), nombre,
                equipo.getId(), "Forward", null, "Argentina", true));
    }

    private EquipoEntity nuevoEquipo(String nombre) {
        Instant ahora = Instant.now();
        return equipos.saveAndFlush(EquipoEntity.builder().id(UUID.randomUUID()).nombre(nombre)
                .liga("Premier League").activo(true).creadoEn(ahora).actualizadoEn(ahora).build());
    }
}
