package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.impl.SincronizacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionCatalogoService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CatalogoControllerTest {
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    @Mock SincronizacionCatalogoService servicio;
    private CatalogoController controller;

    @BeforeEach void preparar() { controller = new CatalogoController(servicio); }

    @Test
    void jwtValidoSinCuerpoDevuelveConteos() {
        when(servicio.sincronizar()).thenReturn(new SincronizacionResultado(96, 120, 96, 0, 120, 0));
        var respuesta = controller.sincronizar(USUARIO, null);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody().data().equiposProcesados()).isEqualTo(96);
        assertThat(respuesta.getBody().data().jugadoresProcesados()).isEqualTo(120);
        verify(servicio).validarUsuario(USUARIO);
        verify(servicio).sincronizar();
    }

    @Test
    void cuerpoNoVacioEs400YNoIniciaSincronizacion() {
        assertThatThrownBy(() -> controller.sincronizar(USUARIO, "{}"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(servicio);
    }

    @Test
    void lockOcupadoEs409YProveedorEs503() {
        doThrow(new SincronizacionEnCursoException()).when(servicio).sincronizar();
        assertThatThrownBy(() -> controller.sincronizar(USUARIO, null)).isInstanceOf(SincronizacionEnCursoException.class);
        reset(servicio);
        doThrow(new com.devchesterunited.devchesterunitedv2.adapter.FootballDataException("interno"))
                .when(servicio).sincronizar();
        assertThatThrownBy(() -> controller.sincronizar(USUARIO, null))
                .isInstanceOf(com.devchesterunited.devchesterunitedv2.adapter.FootballDataException.class);
    }
}
