package com.devchesterunited.devchesterunitedv2.configuration;

import com.devchesterunited.devchesterunitedv2.service.dto.SincronizacionEstadisticasResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EstadisticasScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EstadisticasScheduler.class);

    private final SincronizacionEstadisticasJugadorService sincronizacion;

    public EstadisticasScheduler(
            SincronizacionEstadisticasJugadorService sincronizacion) {
        this.sincronizacion = sincronizacion;
    }

    @Scheduled(cron = "${estadisticas.scheduler.cron:-}")
    public void ejecutarSemanal() {
        LOGGER.info(
                "Iniciando sincronización semanal de estadísticas desde WhoScored"
        );

        try {
            SincronizacionEstadisticasResultado resultado =
                    sincronizacion.sincronizar();

            LOGGER.info(
                    "Sincronización semanal de estadísticas desde WhoScored finalizada correctamente: {}",
                    resultado
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Error durante la sincronización semanal de estadísticas desde WhoScored",
                    exception
            );

            throw new IllegalStateException(
                    "Falló la sincronización semanal de estadísticas desde WhoScored",
                    exception
            );
        }
    }
}
