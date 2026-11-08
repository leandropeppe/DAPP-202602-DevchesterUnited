package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Valida el usuario autorizado para sincronizar el catalogo. */
@Component
public class CatalogoUsuarioValidator {
    private final UsuarioRepository usuarios;

    public CatalogoUsuarioValidator(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public void validar(UUID usuarioId) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) {
            throw new BadCredentialsException("No autorizado.");
        }
    }
}
