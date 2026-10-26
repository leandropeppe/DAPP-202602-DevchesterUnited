package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.GlobalExceptionHandler;
import com.devchesterunited.devchesterunitedv2.exception.SincronizacionPersistenciaException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SyncPersistenceErrorHandlerTest {
    @Test
    void persistenciaDuranteSyncEs503YNo409() {
        var respuesta = new GlobalExceptionHandler().syncPersistenceUnavailable(
                new SincronizacionPersistenciaException(new DataIntegrityViolationException("interno")));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
