package com.devchesterunited.devchesterunitedv2.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.market.AsignacionCreditosInicialesService;
import com.devchesterunited.devchesterunitedv2.service.impl.market.InicializacionCreditosUsuariosService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InicializacionCreditosUsuariosServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock AsignacionCreditosInicialesService asignaciones;

    @Test
    void procesaUsuariosEnOrdenYDelegaCadaUnoEnUnaOperacionIdempotente() {
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(usuarios.findIdsOrderById()).thenReturn(List.of(primero, segundo));

        new InicializacionCreditosUsuariosService(usuarios, asignaciones)
                .asignarAUsuariosExistentes();

        InOrder orden = inOrder(asignaciones);
        orden.verify(asignaciones).asignarParaUsuario(primero);
        orden.verify(asignaciones).asignarParaUsuario(segundo);
        verify(usuarios).findIdsOrderById();
    }
}
