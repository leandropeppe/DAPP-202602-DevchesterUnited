package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Procesa usuarios existentes en transacciones independientes por usuario. */
@Service
public class InicializacionCreditosUsuariosService {
    private final UsuarioRepository usuarios;
    private final AsignacionCreditosInicialesService asignaciones;

    public InicializacionCreditosUsuariosService(
            UsuarioRepository usuarios,
            AsignacionCreditosInicialesService asignaciones) {
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
    }

    public void asignarAUsuariosExistentes() {
        for (UUID usuarioId : usuarios.findIdsOrderById()) {
            asignaciones.asignarParaUsuario(usuarioId);
        }
    }
}
