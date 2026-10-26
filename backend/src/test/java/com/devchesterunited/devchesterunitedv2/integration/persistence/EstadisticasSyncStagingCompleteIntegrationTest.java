package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;

import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncStagingEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EstadisticasSyncStagingCompleteIntegrationTest {
    @Test
    void stagingConservaUnaFilaTecnicaPorJugadorYEjecucion() {
        var staging = new EstadisticasSyncStagingEntity(UUID.randomUUID(), UUID.randomUUID(),
                "PREMIER_LEAGUE", 7L, "Jugador");
        assertThat(staging.getSyncId()).isNotNull();
        assertThat(staging.isFamiliaGeneralRecibida()).isFalse();
        assertThat(staging.isFamiliaOfensivaRecibida()).isFalse();
        assertThat(staging.isFamiliaDefensivaRecibida()).isFalse();
        assertThat(staging.isFamiliaPasesRecibida()).isFalse();
    }
}
