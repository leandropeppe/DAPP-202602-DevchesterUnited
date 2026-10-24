package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CotizacionPorEstrategiaIntegrationTest extends PostgresTestSupport {
    private static final UUID PLAYER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    @Autowired private EstrategiaValorizacionRepository estrategias;
    @Autowired private EjecucionValorizacionRepository ejecuciones;
    @Autowired private CotizacionActualEstrategiaRepository actuales;
    @Autowired private HistoricoCotizacionEstrategiaRepository historicos;

    @AfterEach
    void limpiar() {
        historicos.deleteAll();
        actuales.deleteAll();
        ejecuciones.deleteAll();
        estrategias.deleteAll();
    }

    @Test
    void conservaCotizacionActualEHistoricoSeparadosPorEstrategia() {
        Instant ahora = Instant.now();
        estrategias.save(new EstrategiaValorizacionEntity("GENERAL", 1, "General", "GENERAL", true, false,
                new BigDecimal("1.00"), new BigDecimal("99.00"), "{}"));
        estrategias.save(new EstrategiaValorizacionEntity("POSITIONAL", 1, "Positional", "POSICIONAL", true, true,
                new BigDecimal("1.00"), new BigDecimal("99.00"), "{}"));
        UUID ejecucion = UUID.randomUUID();
        ejecuciones.save(new EjecucionValorizacionEntity(ejecucion, "MANUAL", ahora, ahora, "INICIADA"));
        actuales.save(new CotizacionActualEstrategiaEntity(PLAYER_ID, "GENERAL", 1, ejecucion,
                new BigDecimal(".50"), new BigDecimal("50.50"), new BigDecimal("1.00"), new BigDecimal("99.00"), ahora));
        actuales.save(new CotizacionActualEstrategiaEntity(PLAYER_ID, "POSITIONAL", 1, ejecucion,
                new BigDecimal(".70"), new BigDecimal("70.30"), new BigDecimal("1.00"), new BigDecimal("99.00"), ahora));
        assertThat(actuales.findByJugadorId(PLAYER_ID)).hasSize(2);
        assertThat(historicos.findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(
                PLAYER_ID, "GENERAL", 1)).isEmpty();
        assertThat(estrategias.findByHabilitadaParaCalculoTrue()).hasSize(2);
    }
}
