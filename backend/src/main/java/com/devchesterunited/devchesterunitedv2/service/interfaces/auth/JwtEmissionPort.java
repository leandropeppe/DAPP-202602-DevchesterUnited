package com.devchesterunited.devchesterunitedv2.service.interfaces.auth;

import java.util.UUID;

public interface JwtEmissionPort {
    String emitir(UUID usuarioId);
}
