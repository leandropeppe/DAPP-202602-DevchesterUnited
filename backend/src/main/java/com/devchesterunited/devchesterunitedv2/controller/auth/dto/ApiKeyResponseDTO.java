package com.devchesterunited.devchesterunitedv2.controller.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ApiKeyResponseDTO(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("prefix") String prefijo,
        @JsonProperty("is_active") boolean activa,
        @JsonProperty("created_at") Instant creadaEn) {
    public ApiKeyResponseDTO {
        if (apiKey == null || !apiKey.matches("dev_key_[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("El formato de la API Key no es válido.");
        }
        if (prefijo == null || prefijo.length() != 14 || !apiKey.startsWith(prefijo)) {
            throw new IllegalArgumentException("El prefijo de la API Key no es válido.");
        }
        if (!activa || creadaEn == null) throw new IllegalArgumentException("La respuesta de API Key no es válida.");
    }

    @JsonIgnore
    public String getApiKey() { return apiKey; }

    @Override
    public String toString() {
        return "ApiKeyResponseDTO[prefijo=" + prefijo + ", activa=" + activa + ", creadaEn=" + creadaEn + "]";
    }
}
