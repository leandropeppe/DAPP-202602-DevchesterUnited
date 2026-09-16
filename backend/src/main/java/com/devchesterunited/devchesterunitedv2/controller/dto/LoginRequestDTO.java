package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.controller.dto.validation.ContrasenaValida;
import com.devchesterunited.devchesterunitedv2.controller.dto.validation.TextoJsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LoginRequestDTO(
        @JsonProperty("username")
        @JsonDeserialize(using = TextoJsonDeserializer.class)
        @NotBlank(message = "El nombre de usuario es obligatorio.")
        @Size(min = 3, max = 30, message = "El nombre de usuario debe tener entre 3 y 30 caracteres.")
        @Pattern(regexp = "[a-z0-9]+", message = "El nombre de usuario debe contener solo letras ASCII y dígitos.")
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
