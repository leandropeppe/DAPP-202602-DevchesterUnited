package com.devchesterunited.devchesterunitedv2.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.controller.estadisticas.dto.EstadisticasJugadorResponseDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class JugadorDetalleCompleteStatisticsE2ETest {
    @Test
    void contratoDeDetalleContieneLasCuatroFamilias() throws Exception {
        var dto = new EstadisticasJugadorResponseDTO(null, null, null, null, null, null, null, null, null, null, null,
                new EstadisticasJugadorResponseDTO.Generales(1, 0, 10, 0, null, null, null, null),
                new EstadisticasJugadorResponseDTO.Ofensivas(1, 1, null, null, null, null, null, null, null),
                new EstadisticasJugadorResponseDTO.Defensivas(null, null, null, null, null, null, null, 0),
                new EstadisticasJugadorResponseDTO.Pases(null, new BigDecimal("86.44"), null, null, null));
        var tree = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(dto));
        assertThat(tree.fieldNames()).toIterable().contains("generales", "ofensivas", "defensivas", "pases");
    }
}
