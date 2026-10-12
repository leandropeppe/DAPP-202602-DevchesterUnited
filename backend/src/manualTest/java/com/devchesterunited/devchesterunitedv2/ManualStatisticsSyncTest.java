package com.devchesterunited.devchesterunitedv2;

import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ManualStatisticsSyncTest {

    @Autowired
    private SincronizacionEstadisticasJugadorService service;

    @Test
    void sincronizarEstadisticasRealesUnaVez() {
        try {
            service.sincronizar();
            System.out.println("SINCRONIZACIÓN DE ESTADÍSTICAS: PUBLICADA");
        } catch (Throwable failure) {
            System.err.println("SINCRONIZACIÓN DE ESTADÍSTICAS: FALLIDA");
            throw failure;
        }
    }
}
