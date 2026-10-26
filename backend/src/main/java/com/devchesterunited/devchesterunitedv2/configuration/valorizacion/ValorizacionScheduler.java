package com.devchesterunited.devchesterunitedv2.configuration.valorizacion;

import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.ValorizacionJugadorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ValorizacionScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ValorizacionScheduler.class);

    private final ValorizacionJugadorService valorizacion;
    private final ValorizacionEjecucionConfig configuracion;

    public ValorizacionScheduler(
            ValorizacionJugadorService valorizacion,
            ValorizacionEjecucionConfig configuracion) {

        this.valorizacion = valorizacion;
        this.configuracion = configuracion;
    }

    @Scheduled(cron = "${valorizacion.scheduler.cron:-}")
    public void ejecutarSemanal() {

        LOGGER.info("Iniciando ejecución programada de valorización de jugadores");

        try {
            valorizacion.ejecutarSemanal(
                    configuracion.configuracionElegibilidad()
            );

            LOGGER.info("Ejecución programada de valorización finalizada correctamente");

        } catch (RuntimeException exception) {

            LOGGER.error(
                    "Error durante la ejecución programada de valorización",
                    exception
            );

            throw new IllegalStateException(
                    "Falló la ejecución programada de valorización de jugadores",
                    exception
            );
        }
    }
}
