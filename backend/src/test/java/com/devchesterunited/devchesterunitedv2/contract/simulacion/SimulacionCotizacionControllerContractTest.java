package com.devchesterunited.devchesterunitedv2.contract.simulacion;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionScheduler;
import com.devchesterunited.devchesterunitedv2.controller.jugador.JugadorController;
import com.devchesterunited.devchesterunitedv2.exception.GlobalExceptionHandler;
import com.devchesterunited.devchesterunitedv2.exception.JugadorNoEncontradoException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CodigoErrorSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ExcepcionSimulacion;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.simulacion.SimulacionCotizacionService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.CotizacionConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SimulacionCotizacionControllerContractTest {
    private MockMvc mvc;
    private SimulacionCotizacionService service;

    @BeforeEach
    void setUp() {
        service = mock(SimulacionCotizacionService.class);
        JugadorController controller = new JugadorController(mock(JugadorService.class),
                mock(CotizacionConsultaService.class), mock(ValorizacionScheduler.class), service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void getExponeConfiguracionSinPoblacion() throws Exception {
        when(service.obtenerConfiguracion("ply-1")).thenReturn(new SimulacionConfiguracionDTO(
                "ply-1", List.of("Forward"), "Forward", "GENERAL", 1, false, null,
                List.of(3, 5, 8, 10), 10, 900, List.of(), List.of()));

        mvc.perform(get("/api/v1/players/ply-1/valuation-simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentQuote").doesNotExist())
                .andExpect(jsonPath("$.data.horizons[3]").value(10))
                .andExpect(jsonPath("$.data.blockingReasons").isArray());
        verify(service).obtenerConfiguracion("ply-1");
    }

    @Test
    void postSoloAceptaContratoDeSimulacionYDevuelveResultado() throws Exception {
        when(service.simular(eq("ply-1"), any())).thenReturn(new SimulacionResultadoDTO(
                "ply-1", 5, "GENERAL", 1, "Forward", BigDecimal.TEN, BigDecimal.valueOf(12),
                BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(20), null,
                Map.of("apps", 15), Map.of()));

        mvc.perform(post("/api/v1/players/ply-1/valuation-simulator")
                        .contentType("application/json")
                        .content("{\"horizonMatches\":5,\"modifications\":{\"GOALS\":2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectedStatistics.apps").value(15))
                .andExpect(jsonPath("$.data.percentageDifference").value(20));
        verify(service).simular(eq("ply-1"), any());
    }

    @Test
    void postRechazaHorizonteYCamposAdicionales() throws Exception {
        mvc.perform(post("/api/v1/players/ply-1/valuation-simulator")
                        .contentType("application/json")
                        .content("{\"horizonMatches\":4,\"modifications\":{}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/players/ply-1/valuation-simulator")
                        .contentType("application/json")
                        .content("{\"horizonMatches\":5,\"modifications\":{\"apps\":99}}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void mapeaJugadorInexistenteA404YEscenarioNoSimulableA409() throws Exception {
        when(service.obtenerConfiguracion("missing")).thenThrow(new JugadorNoEncontradoException());
        mvc.perform(get("/api/v1/players/missing/valuation-simulator"))
                .andExpect(status().isNotFound());

        when(service.simular(eq("ply-1"), any())).thenThrow(new ExcepcionSimulacion(
                CodigoErrorSimulacion.DATOS_INSUFICIENTES, "Faltan estadísticas suficientes para simular."));
        mvc.perform(post("/api/v1/players/ply-1/valuation-simulator")
                        .contentType("application/json")
                        .content("{\"horizonMatches\":5,\"modifications\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Faltan estadísticas suficientes para simular."));
    }
}
