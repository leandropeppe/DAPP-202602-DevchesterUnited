package com.devchesterunited.devchesterunitedv2.contract.simulacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionCotizacionRequestDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.SimulacionLecturaMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulacionApiSerializationContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializaYDeserializaModificationsSinCamposOficiales() throws Exception {
        SimulacionCotizacionRequestDTO request = new SimulacionCotizacionRequestDTO(5,
                Map.of("GOALS", BigDecimal.valueOf(3), "SHOTS", BigDecimal.valueOf(4.2)));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));
        SimulacionCotizacionRequestDTO roundTrip = objectMapper.readValue(json.toString(),
                SimulacionCotizacionRequestDTO.class);

        assertEquals(5, json.get("horizonMatches").asInt());
        assertEquals(3, roundTrip.modifications().get("GOALS").intValue());
        assertFalse(json.has("apps"));
        assertFalse(json.has("minsPlayed"));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulacionCotizacionRequestDTO(5, Map.of("apps", BigDecimal.ONE)));
    }

    @Test
    void serializaConfiguracionConNulosTiposRangosYBloqueos() throws Exception {
        SimulacionConfiguracionDTO config = new SimulacionConfiguracionDTO(
                "ply_123", List.of("Forward", "Midfielder"), null, "POSITIONAL", 1, true,
                null, List.of(3, 5, 8, 10), null, 900, List.of(), List.of("POSICION_AMBIGUA"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(config));

        assertEquals("ply_123", json.get("playerId").asText());
        assertTrue(json.get("resolvedPosition").isNull());
        assertTrue(json.get("currentQuote").isNull());
        assertTrue(json.get("currentApps").isNull());
        assertEquals("POSICION_AMBIGUA", json.get("blockingReasons").get(0).asText());
        assertEquals(4, json.get("horizons").size());
    }

    @Test
    void serializaResultadoConProjectedStatisticsYDiferenciasNulas() throws Exception {
        ResultadoSimulacion resultado = new ResultadoSimulacion(UUID.randomUUID(), 5, "GENERAL", 1,
                "Forward", null, BigDecimal.valueOf(120), BigDecimal.valueOf(.75),
                new EstadisticasProyectadas(metricas()));

        SimulacionResultadoDTO dto = new SimulacionLecturaMapper().aResultado(resultado);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertTrue(json.get("currentQuote").isNull());
        assertTrue(json.get("monetaryDifference").isNull());
        assertTrue(json.get("percentageDifference").isNull());
        assertEquals("COTIZACION_ACTUAL_AUSENTE", json.get("percentageDifferenceUnavailableReason").asText());
        assertTrue(json.get("projectedStatistics").has("apps"));
        assertTrue(json.get("projectedStatistics").get("apps").isNull());
    }

    private static MetricasEstadisticasJugador metricas() {
        return new MetricasEstadisticasJugador(
                new MetricasGenerales(null, null, null, null, null, null, null, null),
                new MetricasOfensivas(null, null, null, null, null, null, null, null, null),
                new MetricasDefensivas(null, null, null, null, null, null, null, null),
                new MetricasPases(null, null, null, null, null));
    }
}
