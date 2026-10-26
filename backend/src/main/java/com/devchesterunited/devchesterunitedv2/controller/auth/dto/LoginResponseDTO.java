package com.devchesterunited.devchesterunitedv2.controller.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Datos de respuesta; no genera ni valida JWT. */
public record LoginResponseDTO(@JsonProperty("access_token") String tokenAcceso) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getAccessToken() { return tokenAcceso; }

    @JsonProperty("token_type")
    public String tipoToken() { return "Bearer"; }

    @JsonProperty("expires_in")
    public int expiraEn() { return 3600; }

    @Override
    public String toString() { return "LoginResponseDTO[token omitido]"; }
}
