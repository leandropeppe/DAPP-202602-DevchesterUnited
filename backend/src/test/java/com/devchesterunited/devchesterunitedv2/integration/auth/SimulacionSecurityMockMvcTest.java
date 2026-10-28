package com.devchesterunited.devchesterunitedv2.integration.auth;

import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityErrorHandler;
import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionScheduler;
import com.devchesterunited.devchesterunitedv2.controller.jugador.JugadorController;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.simulacion.SimulacionCotizacionService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.CotizacionConsultaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JugadorController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class SimulacionSecurityMockMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean JugadorService jugadores;
    @MockitoBean CotizacionConsultaService cotizaciones;
    @MockitoBean ValorizacionScheduler scheduler;
    @MockitoBean SimulacionCotizacionService simulacion;

    @Test
    void postSinJwtEs401YGetConservaPoliticaPublica() throws Exception {
        mvc.perform(post("/api/v1/players/ply-1/valuation-simulator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonMatches\":3,\"modifications\":{}}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/players"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        verifyNoInteractions(simulacion);
    }

    @Test
    void postConJwtValidoDeUsuarioNormalEs200() throws Exception {
        when(simulacion.simular(eq("ply-1"), any())).thenReturn(new SimulacionResultadoDTO(
                "ply-1", 3, "GENERAL", 1, "Forward", BigDecimal.TEN, BigDecimal.valueOf(12),
                BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(20), null,
                Map.of(), Map.of()));

        mvc.perform(post("/api/v1/players/ply-1/valuation-simulator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonMatches\":3,\"modifications\":{}}")
                        .with(jwt()))
                .andExpect(status().isOk());

        verify(simulacion).simular(eq("ply-1"), any());
    }

    @Test
    void postConJwtInvalidoEs401() throws Exception {
        mvc.perform(post("/api/v1/players/ply-1/valuation-simulator")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonMatches\":3,\"modifications\":{}}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(simulacion);
    }
}
