package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.controller.dto.validation.ContrasenaValida;
import com.devchesterunited.devchesterunitedv2.controller.dto.validation.TextoJsonDeserializer;
import com.devchesterunited.devchesterunitedv2.controller.dto.validation.UsernameValido;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;

public record LoginRequestDTO(
        @JsonProperty("username")
        @JsonDeserialize(using = TextoJsonDeserializer.class)
        @NotBlank(message = "El nombre de usuario es obligatorio.")
        @UsernameValido
        String nombreUsuario,
        @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
        @JsonDeserialize(using = TextoJsonDeserializer.class)
        @NotNull(message = "La contraseña es obligatoria.")
        @ContrasenaValida(message = "La contraseña debe tener entre 1 y 72 caracteres y como máximo 72 bytes UTF-8.")
        String contrasena) {
    public LoginRequestDTO {
        nombreUsuario = nombreUsuario == null ? null : nombreUsuario.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return "LoginRequestDTO[credenciales omitidas]"; }
}
