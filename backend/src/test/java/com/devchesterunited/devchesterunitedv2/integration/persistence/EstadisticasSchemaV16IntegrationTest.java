package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EstadisticasSchemaV16IntegrationTest extends PostgresTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void V16ExponeLasMetricasCompletasYLaUnicidadPorJugador() {
        Integer columnas = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'jugador_estadisticas'
                and column_name in ('apps','sub_on','man_of_the_match','aerial_won_per_game',
                'foul_given_per_game','offside_given_per_game','dispossessed_per_game','turnover_per_game',
                'interception_per_game','fouls_per_game','offside_won_per_game','clearance_per_game',
                'was_dribbled_per_game','outfielder_block_per_game','goal_own','total_passes_per_game',
                'pass_success','accurate_crosses_per_game','accurate_long_pass_per_game',
                'accurate_through_ball_per_game')
                """, Integer.class);
        Integer restricciones = jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'jugador_estadisticas' and constraint_type = 'UNIQUE'
                """, Integer.class);
        assertThat(columnas).isEqualTo(20);
        assertThat(restricciones).isGreaterThanOrEqualTo(1);
    }
}
