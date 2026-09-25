package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.ApiKeyResultado;
import java.util.UUID;

public interface ApiKeyService {
    ApiKeyResultado generar(UUID usuarioId);
    UUID validar(String apiKey);
}
