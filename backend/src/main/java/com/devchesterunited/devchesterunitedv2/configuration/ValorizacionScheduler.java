package com.devchesterunited.devchesterunitedv2.configuration;

import com.devchesterunited.devchesterunitedv2.service.config.ValorizacionEjecucionConfig;
import com.devchesterunited.devchesterunitedv2.service.interfaces.ValorizacionJugadorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ValorizacionScheduler {
    private final ValorizacionJugadorService valorizacion;
    private final ValorizacionEjecucionConfig configuracion;

    public ValorizacionScheduler(ValorizacionJugadorService valorizacion, ValorizacionEjecucionConfig configuracion) {
        this.valorizacion = valorizacion;
        this.configuracion = configuracion;
    }

    @Scheduled(cron = "${valorizacion.scheduler.cron:-}")
    public void ejecutarSemanal() {
        valorizacion.ejecutarSemanal(configuracion.configuracionElegibilidad());
    }
}
