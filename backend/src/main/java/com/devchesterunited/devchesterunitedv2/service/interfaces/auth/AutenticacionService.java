package com.devchesterunited.devchesterunitedv2.service.interfaces.auth;

import com.devchesterunited.devchesterunitedv2.service.dto.auth.ApiKeyResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.LoginResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.UsuarioResultado;
import java.util.UUID;

public interface AutenticacionService {
    UsuarioResultado registrar(String nombreUsuario, String correo, String contrasena);

    LoginResultado login(String nombreUsuario, String contrasena);

    ApiKeyResultado generarApiKey(UUID usuarioId);
}
