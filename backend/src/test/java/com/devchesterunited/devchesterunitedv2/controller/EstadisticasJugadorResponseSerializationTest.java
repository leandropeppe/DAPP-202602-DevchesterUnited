package com.devchesterunited.devchesterunitedv2.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.controller.estadisticas.dto.EstadisticasJugadorResponseDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EstadisticasJugadorResponseSerializationTest {
    @Test
    void serializaLasCuatroFamiliasYConservaElPorcentaje() throws Exception {
        var dto = new EstadisticasJugadorResponseDTO(null, null, null, null, null, null, null, null, null, null, null,
                new EstadisticasJugadorResponseDTO.Generales(null, null, 900, null, null, null, null, null),
                new EstadisticasJugadorResponseDTO.Ofensivas(8, 4, null, null, null, null, null, null, null),
                new EstadisticasJugadorResponseDTO.Defensivas(null, null, null, null, null, null, null, 0),
                new EstadisticasJugadorResponseDTO.Pases(null, new BigDecimal("86.44"), null, null, null));
        String json = new ObjectMapper().writeValueAsString(dto);
        assertThat(json).contains("\"generales\"", "\"ofensivas\"", "\"defensivas\"", "\"pases\"", "86.44");
    }
}
