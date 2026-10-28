package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionCotizacionRequestDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.simulacion.SimulacionCotizacionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulacionCotizacionServiceTest {
    @Test
    void elFakeDelContratoExponeConfiguracionSinCargarPoblacion() {
        FakeSimulacionService fake = new FakeSimulacionService();

        SimulacionConfiguracionDTO configuracion = fake.obtenerConfiguracion("ply-1");

        assertEquals("ply-1", configuracion.playerId());
        assertEquals(List.of(3, 5, 8, 10), configuracion.horizons());
        assertEquals(BigDecimal.valueOf(100), configuracion.currentQuote());
        assertEquals(1, fake.cargasPoblacion);
    }

    @Test
    void elFakeDelContratoRepresentaDiferenciasYPorcentajeNoCalculable() {
        FakeSimulacionService fake = new FakeSimulacionService();

        SimulacionResultadoDTO resultado = fake.simular("ply-1",
                new SimulacionCotizacionRequestDTO(5, Map.of("GOALS", BigDecimal.valueOf(3))));
        SimulacionResultadoDTO sinCotizacion = fake.resultadoSinCotizacion();

        assertEquals(BigDecimal.valueOf(120), resultado.simulatedQuote());
        assertEquals(BigDecimal.valueOf(20), resultado.monetaryDifference());
        assertEquals(BigDecimal.valueOf(20), resultado.percentageDifference());
        assertNull(sinCotizacion.percentageDifference());
        assertEquals("COTIZACION_ACTUAL_AUSENTE", sinCotizacion.percentageDifferenceUnavailableReason());
    }

    private static final class FakeSimulacionService implements SimulacionCotizacionService {
        private int cargasPoblacion;

        @Override
        public SimulacionConfiguracionDTO obtenerConfiguracion(String playerId) {
            cargasPoblacion++;
            return new SimulacionConfiguracionDTO(playerId, List.of("Forward"), "Forward",
                    "GENERAL", 1, false, BigDecimal.valueOf(100), List.of(3, 5, 8, 10),
                    10, 900, List.of(), List.of());
        }

        @Override
        public SimulacionResultadoDTO simular(String playerId, SimulacionCotizacionRequestDTO solicitud) {
            return new SimulacionResultadoDTO(playerId, solicitud.horizonMatches(), "GENERAL", 1,
                    "Forward", BigDecimal.valueOf(100), BigDecimal.valueOf(120), BigDecimal.valueOf(.8),
                    BigDecimal.valueOf(20), BigDecimal.valueOf(20), null, Map.of(), Map.of());
        }

        private SimulacionResultadoDTO resultadoSinCotizacion() {
            return new SimulacionResultadoDTO("ply-1", 5, "GENERAL", 1, "Forward", null,
                    BigDecimal.valueOf(120), BigDecimal.valueOf(.8), null, null,
                    "COTIZACION_ACTUAL_AUSENTE", Map.of(), Map.of());
        }
    }
}
