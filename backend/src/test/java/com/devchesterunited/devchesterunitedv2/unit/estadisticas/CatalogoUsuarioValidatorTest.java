package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoUsuarioValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogoUsuarioValidatorTest {
    @Test
    void aceptaUsuarioExistente() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        UUID usuarioId = UUID.randomUUID();
        when(usuarios.existsById(usuarioId)).thenReturn(true);

        new CatalogoUsuarioValidator(usuarios).validar(usuarioId);

        verify(usuarios).existsById(usuarioId);
    }

    @Test
    void rechazaUsuarioInexistente() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        UUID usuarioId = UUID.randomUUID();
        when(usuarios.existsById(usuarioId)).thenReturn(false);

        assertThatThrownBy(() -> new CatalogoUsuarioValidator(usuarios).validar(usuarioId))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("No autorizado.");
    }

    @Test
    void rechazaIdentificadorNuloSinConsultarRepositorio() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);

        assertThatThrownBy(() -> new CatalogoUsuarioValidator(usuarios).validar(null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("No autorizado.");
    }
}
