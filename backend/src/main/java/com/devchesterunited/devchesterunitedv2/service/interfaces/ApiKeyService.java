package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.controller.dto.ApiKeyResponseDTO;
import java.util.UUID;

public interface ApiKeyService {
    ApiKeyResponseDTO generar(UUID usuarioId);
    UUID validar(String apiKey);
}
