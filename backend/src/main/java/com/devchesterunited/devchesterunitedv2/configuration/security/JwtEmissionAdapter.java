package com.devchesterunited.devchesterunitedv2.configuration.security;

import com.devchesterunited.devchesterunitedv2.service.interfaces.auth.JwtEmissionPort;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtEmissionAdapter implements JwtEmissionPort {
    private final JwtEncoder encoder;
    private final JwtEmissionParametersFactory parametersFactory;

    public JwtEmissionAdapter(JwtEncoder encoder, JwtEmissionParametersFactory parametersFactory) {
        this.encoder = encoder;
        this.parametersFactory = parametersFactory;
    }

    @Override
    public String emitir(UUID usuarioId) {
        return encoder.encode(parametersFactory.crear(usuarioId)).getTokenValue();
    }
}
