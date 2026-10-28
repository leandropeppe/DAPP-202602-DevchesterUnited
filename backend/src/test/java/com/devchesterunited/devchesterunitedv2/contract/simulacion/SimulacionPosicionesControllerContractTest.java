package com.devchesterunited.devchesterunitedv2.contract.simulacion;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionScheduler;
import com.devchesterunited.devchesterunitedv2.controller.jugador.JugadorController;
import com.devchesterunited.devchesterunitedv2.exception.GlobalExceptionHandler;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.EstadisticaSimulacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.TipoMetricaSimulacionDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.simulacion.SimulacionCotizacionService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.CotizacionConsultaService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulacionPosicionesControllerContractTest {
    @Test
    void getExponePosicionAplicadaYEditabilidadSinListasEnFrontend() throws Exception {
        var service = mock(SimulacionCotizacionService.class);
        var controller = new JugadorController(mock(JugadorService.class), mock(CotizacionConsultaService.class),
                mock(ValorizacionScheduler.class), service);
        when(service.obtenerConfiguracion("ply-1")).thenReturn(new SimulacionConfiguracionDTO(
                "ply-1", List.of("FW"), "Forward", "POSITIONAL", 2, false, BigDecimal.TEN,
                List.of(3, 5, 8, 10), 10, 900, List.of(
                        stat("GOALS", true), stat("RATING", true), stat("DRIBBLES", false)), List.of()));

        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/api/v1/players/ply-1/valuation-simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolvedPosition").value("Forward"))
                .andExpect(jsonPath("$.data.strategyVersion").value(2))
                .andExpect(jsonPath("$.data.statistics[1].editable").value(true));
    }

    private static EstadisticaSimulacionDTO stat(String metric, boolean editable) {
        return new EstadisticaSimulacionDTO(metric, metric, TipoMetricaSimulacionDTO.AVERAGE,
                BigDecimal.ONE, editable, BigDecimal.ZERO, null);
    }
}
