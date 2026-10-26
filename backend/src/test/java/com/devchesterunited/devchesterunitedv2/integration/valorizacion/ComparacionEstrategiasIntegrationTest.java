package com.devchesterunited.devchesterunitedv2.integration.valorizacion;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.HistoricoCotizacionEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ComparacionEstrategiasIntegrationTest extends PostgresTestSupport {
    private static final UUID PLAYER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    @Autowired private EstrategiaValorizacionRepository estrategias;
    @Autowired private EjecucionValorizacionRepository ejecuciones;
    @Autowired private CotizacionActualEstrategiaRepository actuales;
    @Autowired private HistoricoCotizacionEstrategiaRepository historicos;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void prepararJugadorFixture() {
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        jdbc.update("""
                INSERT INTO jugadores
                    (id, name, team_id, position, jersey_number, nationality, is_active, created_at, updated_at)
                VALUES (?, 'Jugador de valorizacion', ?, 'Forward', 10, 'Argentina', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """, PLAYER_ID, teamId);
    }

    @AfterEach
    void limpiar() {
        historicos.deleteAll();
        actuales.deleteAll();
        ejecuciones.deleteAll();
        estrategias.deleteAll();
    }

    @Test
    void conservaSeriesSeparadasSinSobrescribirHistoricos() {
        Instant ahora = Instant.now();
        estrategias.save(new EstrategiaValorizacionEntity("GENERAL", 1, "General", "GENERAL", true, false,
                BigDecimal.ZERO, BigDecimal.ONE, "{}"));
        estrategias.save(new EstrategiaValorizacionEntity("POSITIONAL", 1, "Positional", "POSICIONAL", true, false,
                BigDecimal.ZERO, BigDecimal.ONE, "{}"));
        UUID ejecucion = UUID.randomUUID();
        ejecuciones.save(new EjecucionValorizacionEntity(ejecucion, "MANUAL", ahora, ahora, "COMPLETADA"));

        actuales.save(new CotizacionActualEstrategiaEntity(PLAYER_ID, "GENERAL", 1, ejecucion,
                d(".40"), d(".40"), BigDecimal.ZERO, BigDecimal.ONE, ahora));
        actuales.save(new CotizacionActualEstrategiaEntity(PLAYER_ID, "POSITIONAL", 1, ejecucion,
                d(".80"), d(".80"), BigDecimal.ZERO, BigDecimal.ONE, ahora));
        historicos.save(historico(ejecucion, "GENERAL", d(".40"), ahora));
        historicos.save(historico(ejecucion, "POSITIONAL", d(".80"), ahora));

        assertThat(actuales.findByJugadorId(PLAYER_ID)).extracting("estrategiaId")
                .containsExactlyInAnyOrder("GENERAL", "POSITIONAL");
        assertThat(historicos.findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(
                PLAYER_ID, "GENERAL", 1)).hasSize(1);
        assertThat(historicos.findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(
                PLAYER_ID, "POSITIONAL", 1)).hasSize(1);
        assertThat(historicos.findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(
                PLAYER_ID, "GENERAL", 1).get(0).getScore()).isEqualByComparingTo(d(".40"));
        assertThat(historicos.findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(
                PLAYER_ID, "POSITIONAL", 1).get(0).getScore()).isEqualByComparingTo(d(".80"));
    }

    private static HistoricoCotizacionEstrategiaEntity historico(UUID ejecucion, String estrategia,
            BigDecimal score, Instant calculadaAt) {
        return new HistoricoCotizacionEstrategiaEntity(UUID.randomUUID(), PLAYER_ID, estrategia, 1, ejecucion,
                calculadaAt, score, score, BigDecimal.ZERO, BigDecimal.ONE, "{\"estrategia\":\"" + estrategia + "\"}");
    }

    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
