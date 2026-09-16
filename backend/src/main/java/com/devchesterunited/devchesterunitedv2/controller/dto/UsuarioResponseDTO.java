package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Datos públicos del usuario; el Service suministrará el id opaco usr_ más UUID. */
public record UsuarioResponseDTO(
        String id,
        @JsonProperty("username") String nombreUsuario,
        @JsonProperty("email") String correo,
        @JsonProperty("created_at") Instant creadoEn) { }
