package com.devchesterunited.devchesterunitedv2.unit.valorizacion;


import static org.mockito.Mockito.*;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionEjecucionConfig;
import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionScheduler;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.ValorizacionJugadorService;
import org.junit.jupiter.api.Test;


class ValorizacionSchedulerTest {
    @Test
    void delegaEnElCasoDeUsoSemanalConConfiguracionExplicita() {
        ValorizacionJugadorService servicio = mock(ValorizacionJugadorService.class);
        ValorizacionEjecucionConfig configuracion = mock(ValorizacionEjecucionConfig.class);
        ConfiguracionElegibilidad elegibilidad = new ConfiguracionElegibilidad(2, 180);
        when(configuracion.configuracionElegibilidad()).thenReturn(elegibilidad);

        new ValorizacionScheduler(servicio, configuracion).ejecutarSemanal();

        verify(servicio).ejecutarSemanal(elegibilidad);
        verifyNoMoreInteractions(servicio);
    }
}
