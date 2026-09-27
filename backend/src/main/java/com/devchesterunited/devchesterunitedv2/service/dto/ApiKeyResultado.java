package com.devchesterunited.devchesterunitedv2.service.dto;

import java.time.Instant;

public record ApiKeyResultado(String apiKey, String prefijo, boolean activa, Instant creadaEn) {
    @Override
    public String toString() {
        return "ApiKeyResultado[prefijo=" + prefijo + ", activa=" + activa + ", creadaEn=" + creadaEn + "]";
    }
}
