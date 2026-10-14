package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncStagingEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EstadisticasSchemaRepositoryIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-10-07T12:00:00Z");

    @Autowired
    private EstadisticasJugadorRepository estadisticas;
    @Autowired
    private EstadisticasSyncRepository sincronizaciones;
    @Autowired
    private EstadisticasSyncStagingRepository staging;
    @Autowired
    private JugadorRepository jugadores;

    @Test
    void persisteEjecucionStagingYEstadisticaPublicada() {
        UUID jugadorId = jugadores.findAll().stream().findFirst().orElseThrow().getId();
        UUID syncId = UUID.randomUUID();
        sincronizaciones.saveAndFlush(new EstadisticasSyncEntity(
                syncId, EstadoEstadisticasSync.INICIADA, AHORA));

        staging.saveAndFlush(new EstadisticasSyncStagingEntity(
                UUID.randomUUID(), syncId, "PREMIER_LEAGUE", 7001L, "Jugador externo"));
        estadisticas.saveAndFlush(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugadorId).posicion("Forward").minutosJugados(900)
                .goles(2).asistencias(1).tarjetasAmarillas(1).tarjetasRojas(0)
                .syncId(syncId).creadoEn(AHORA).actualizadoEn(AHORA)));

        assertThat(sincronizaciones.findById(syncId)).isPresent();
        assertThat(staging.findAllBySyncId(syncId)).hasSize(1);
        assertThat(estadisticas.findByJugadorId(jugadorId)).isPresent();
    }

    @Test
    void impideDosFotografiasParaElMismoJugador() {
        UUID jugadorId = jugadores.findAll().stream().findFirst().orElseThrow().getId();
        UUID syncId = UUID.randomUUID();
        sincronizaciones.saveAndFlush(new EstadisticasSyncEntity(
                syncId, EstadoEstadisticasSync.PUBLICADA, AHORA));
        estadisticas.saveAndFlush(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugadorId).syncId(syncId)
                .creadoEn(AHORA).actualizadoEn(AHORA)));

        var duplicateStatistics = new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugadorId).syncId(syncId)
                .creadoEn(AHORA).actualizadoEn(AHORA));
        assertThatThrownBy(() -> estadisticas.saveAndFlush(duplicateStatistics))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideEstadisticaSinEjecucionExistente() {
        UUID jugadorId = jugadores.findAll().stream().findFirst().orElseThrow().getId();
        var statisticsWithoutSync = new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugadorId).syncId(UUID.randomUUID())
                .creadoEn(AHORA).actualizadoEn(AHORA));
        assertThatThrownBy(() -> estadisticas.saveAndFlush(statisticsWithoutSync))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
