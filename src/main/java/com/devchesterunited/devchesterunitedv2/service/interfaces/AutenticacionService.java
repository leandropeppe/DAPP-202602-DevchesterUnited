package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.controller.dto.LoginResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.ApiKeyResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.UsuarioResponseDTO;
import java.util.UUID;

public interface AutenticacionService {
    UsuarioResponseDTO registrar(String nombreUsuario, String correo, String contrasena);

    LoginResponseDTO login(String nombreUsuario, String contrasena);

    ApiKeyResponseDTO generarApiKey(UUID usuarioId);
}
