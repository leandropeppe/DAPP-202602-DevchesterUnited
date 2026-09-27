package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.ApiKeyResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.LoginResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.UsuarioResultado;
import java.util.UUID;

public interface AutenticacionService {
    UsuarioResultado registrar(String nombreUsuario, String correo, String contrasena);

    LoginResultado login(String nombreUsuario, String contrasena);

    ApiKeyResultado generarApiKey(UUID usuarioId);
}
