package com.devchesterunited.devchesterunitedv2.service.dto.shared;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResultado(UUID id, String nombreUsuario, String correo, Instant creadoEn) { }
