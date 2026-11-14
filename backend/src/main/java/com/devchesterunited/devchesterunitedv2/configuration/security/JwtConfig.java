package com.devchesterunited.devchesterunitedv2.configuration.security;

import com.devchesterunited.devchesterunitedv2.service.interfaces.auth.JwtEmissionPort;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class JwtConfig implements JwtEmissionPort {
    private static final String EMISOR = "devchester-united";
    private static final String AUDIENCIA = "devchester-api";
    private static final Duration DURACION = Duration.ofSeconds(3600);
    private static final OAuth2Error INVALIDO = new OAuth2Error("invalid_token", "El JWT no es válido.", null);
    private final SecretKey clave;
    private final Clock clock;

    /** jwt.secret se proporciona externamente (por ejemplo, mediante JWT_SECRET). Sin valor por defecto. */
    public JwtConfig(Environment entorno, Clock clock) {
        this.clock = clock;
        this.clave = leerClave(entorno.getProperty("jwt.secret"));
    }

    private static SecretKey leerClave(String secreto) {
        if (secreto == null || secreto.isBlank()) throw configuracionInsegura();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secreto);
        } catch (IllegalArgumentException ignorada) {
            // No conservar la causa: puede contener información del valor configurado.
            throw configuracionInsegura();
        }
        try {
            if (bytes.length < 32 || !Base64.getEncoder().encodeToString(bytes).equals(secreto)) {
                throw configuracionInsegura();
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static IllegalStateException configuracionInsegura() {
        return new IllegalStateException("La configuración JWT requiere un secreto Base64 válido de al menos 32 bytes.");
    }

    @Bean
    public NimbusJwtEncoder jwtEncoder() {
        var jwk = new OctetSequenceKey.Builder(clave).algorithm(JWSAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public NimbusJwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(this::validar);
        return decoder;
    }

    /** Parámetros de emisión para el encoder local. No autentica ni consulta usuarios. */
    public JwtEncoderParameters parametrosPara(UUID usuarioId) {
        if (usuarioId == null) throw new IllegalArgumentException("El identificador del usuario es obligatorio.");
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder().issuer(EMISOR).audience(List.of(AUDIENCIA))
                .subject("usr_" + usuarioId).issuedAt(ahora).expiresAt(ahora.plus(DURACION)).build();
        return JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);
    }

    @Override
    public String emitir(UUID usuarioId) {
        return jwtEncoder().encode(parametrosPara(usuarioId)).getTokenValue();
    }

    private OAuth2TokenValidatorResult validar(Jwt jwt) {
        Instant ahora = clock.instant();
        Instant emitido = jwt.getIssuedAt();
        Instant expira = jwt.getExpiresAt();
        String sujeto = jwt.getSubject();
        boolean valido = EMISOR.equals(jwt.getClaimAsString("iss"))
                && jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCIA)
                && sujeto != null && sujeto.matches("usr_[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                && emitido != null && expira != null
                && Duration.between(emitido, expira).equals(DURACION)
                && !emitido.isAfter(ahora) && ahora.isBefore(expira)
                && (jwt.getNotBefore() == null || !jwt.getNotBefore().isAfter(ahora));
        return valido ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALIDO);
    }
}
