package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstadisticasJugadorCompleteUpsertIntegrationTest {
    @Test
    void builderRepresentaLasTreintaMetricasSinDuplicarLaFilaLogica() {
        var e = new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(UUID.randomUUID()).minutosJugados(1).goles(2).asistencias(3)
                .tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE).regates(BigDecimal.ONE).tackles(BigDecimal.ONE)
                .valoracion(BigDecimal.ONE).tarjetasAmarillas(1).tarjetasRojas(0).apps(1).subOn(1)
                .manOfTheMatch(1).aerialWonPerGame(BigDecimal.ONE).foulGivenPerGame(BigDecimal.ONE)
                .offsideGivenPerGame(BigDecimal.ONE).dispossessedPerGame(BigDecimal.ONE).turnoverPerGame(BigDecimal.ONE)
                .interceptionPerGame(BigDecimal.ONE).foulsPerGame(BigDecimal.ONE).offsideWonPerGame(BigDecimal.ONE)
                .clearancePerGame(BigDecimal.ONE).wasDribbledPerGame(BigDecimal.ONE).outfielderBlockPerGame(BigDecimal.ONE)
                .goalOwn(0).totalPassesPerGame(BigDecimal.ONE).passSuccess(BigDecimal.ONE)
                .accurateCrossesPerGame(BigDecimal.ONE).accurateLongPassPerGame(BigDecimal.ONE)
                .accurateThroughBallPerGame(BigDecimal.ONE).syncId(UUID.randomUUID())
                .creadoEn(Instant.now()).actualizadoEn(Instant.now()));
        assertThat(e.getApps()).isEqualTo(1);
        assertThat(e.getPassSuccess()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(e.getAccurateThroughBallPerGame()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
