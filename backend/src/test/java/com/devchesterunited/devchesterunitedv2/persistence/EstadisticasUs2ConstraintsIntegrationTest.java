package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncStagingEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
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
        estadisticas.saveAndFlush(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugador.getId()).syncId(sync.getId())
                .creadoEn(ahora).actualizadoEn(ahora)));

        var duplicateStatistics = new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugador.getId()).syncId(sync.getId())
                .creadoEn(ahora).actualizadoEn(ahora));
        assertThatThrownBy(() -> estadisticas.saveAndFlush(duplicateStatistics))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideRepetirExternalIdWhoscoredParaJugadoresDiferentes() {
        JugadorEntity otro = nuevoJugador("Otro jugador externo");
        identidadesJugadores.saveAndFlush(new IdentidadExternaJugadorEntity(UUID.randomUUID(),
                jugador, ProveedorIdentidadExterna.WHOSCORED, 99101L));

        var duplicateIdentity = new IdentidadExternaJugadorEntity(
                UUID.randomUUID(), otro, ProveedorIdentidadExterna.WHOSCORED, 99101L);
        assertThatThrownBy(() -> identidadesJugadores.saveAndFlush(duplicateIdentity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideDosIdentidadesWhoscoredParaElMismoJugador() {
        identidadesJugadores.saveAndFlush(new IdentidadExternaJugadorEntity(UUID.randomUUID(),
                jugador, ProveedorIdentidadExterna.WHOSCORED, 99102L));

        var duplicateIdentity = new IdentidadExternaJugadorEntity(
                UUID.randomUUID(), jugador, ProveedorIdentidadExterna.WHOSCORED, 99103L);
        assertThatThrownBy(() -> identidadesJugadores.saveAndFlush(duplicateIdentity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideRepetirExternalIdWhoscoredParaEquiposDiferentes() {
        EquipoEntity otro = nuevoEquipo("Otro equipo externo");
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(UUID.randomUUID(),
                equipo, ProveedorIdentidadExterna.WHOSCORED, 99201L));

        var duplicateIdentity = new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), otro, ProveedorIdentidadExterna.WHOSCORED, 99201L);
        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(duplicateIdentity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideDosIdentidadesWhoscoredParaElMismoEquipo() {
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(UUID.randomUUID(),
                equipo, ProveedorIdentidadExterna.WHOSCORED, 99202L));

        var duplicateIdentity = new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.WHOSCORED, 99203L);
        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(duplicateIdentity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideRepetirJugadorPorSyncCompeticionYProveedor() {
        staging.saveAndFlush(new EstadisticasSyncStagingEntity(UUID.randomUUID(), sync.getId(),
                "PREMIER_LEAGUE", 99301L, "Jugador staging"));

        var duplicateStaging = new EstadisticasSyncStagingEntity(
                UUID.randomUUID(), sync.getId(), "PREMIER_LEAGUE", 99301L, "Jugador staging");
        assertThatThrownBy(() -> staging.saveAndFlush(duplicateStaging))
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
