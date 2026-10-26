package com.devchesterunited.devchesterunitedv2.service.interfaces.auth;

import com.devchesterunited.devchesterunitedv2.service.dto.auth.ApiKeyResultado;

import java.util.UUID;

public interface ApiKeyService {
    ApiKeyResultado generar(UUID usuarioId);
    UUID validar(String apiKey);
}
