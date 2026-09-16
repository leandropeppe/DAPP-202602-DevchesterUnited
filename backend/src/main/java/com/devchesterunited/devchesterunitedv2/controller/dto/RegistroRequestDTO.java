package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.controller.dto.validation.ContrasenaValida;
import com.devchesterunited.devchesterunitedv2.controller.dto.validation.TextoJsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegistroRequestDTO(
        @JsonProperty("username")
        @JsonDeserialize(using = TextoJsonDeserializer.class)
        @NotBlank(message = "El nombre de usuario es obligatorio.")
        @Size(min = 3, max = 30, message = "El nombre de usuario debe tener entre 3 y 30 caracteres.")
        @Pattern(regexp = "[a-z0-9]+", message = "El nombre de usuario debe contener solo letras ASCII y dígitos.")
        String nombreUsuario,
        @JsonProperty("email")
        @JsonDeserialize(using = TextoJsonDeserializer.class)
        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 254, message = "El correo debe tener como máximo 254 caracteres.")
        @Email(message = "El correo debe tener un formato válido.")
        String correo,
        @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
        @JsonDeserialize(using = TextoJsonDeserializer.class)
        @NotNull(message = "La contraseña es obligatoria.")
        @ContrasenaValida(registro = true,
                message = "La contraseña debe tener entre 8 y 72 caracteres, como máximo 72 bytes UTF-8, una mayúscula ASCII, una minúscula ASCII y un dígito, sin controles ni espacios externos.")
        String contrasena) {
    public RegistroRequestDTO {
        nombreUsuario = nombreUsuario == null ? null : nombreUsuario.trim().toLowerCase(Locale.ROOT);
        correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return "RegistroRequestDTO[credenciales omitidas]"; }
}
