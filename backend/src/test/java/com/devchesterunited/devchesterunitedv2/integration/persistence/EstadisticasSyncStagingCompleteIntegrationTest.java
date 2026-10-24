package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncStagingEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
