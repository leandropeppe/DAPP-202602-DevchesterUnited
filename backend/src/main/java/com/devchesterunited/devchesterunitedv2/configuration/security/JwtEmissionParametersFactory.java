package com.devchesterunited.devchesterunitedv2.configuration.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtEmissionParametersFactory {
    private static final String EMISOR = "devchester-united";
    private static final String AUDIENCIA = "devchester-api";
    private static final Duration DURACION = Duration.ofSeconds(3600);

    private final Clock clock;

    public JwtEmissionParametersFactory(Clock clock) {
        this.clock = clock;
    }

    public JwtEncoderParameters crear(UUID usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("El identificador del usuario es obligatorio.");
        }
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder().issuer(EMISOR).audience(List.of(AUDIENCIA))
                .subject("usr_" + usuarioId).issuedAt(ahora).expiresAt(ahora.plus(DURACION)).build();
        return JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);
    }
}
