package com.devchesterunited.devchesterunitedv2.service.dto.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Datos internos permitidos para auditar un intento rechazado. */
public record MarketRejectedAttempt(
        UUID usuarioId,
        String tipoOperacion,
        UUID jugadorId,
        String hashClaveIdempotencia,
        String codigoMotivo,
        String hashParametros,
        Instant creadoEn) {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    public MarketRejectedAttempt {
        tipoOperacion = normalizarOperacion(tipoOperacion);
        codigoMotivo = normalizarMotivo(codigoMotivo);
        validarHash(hashClaveIdempotencia, "hashClaveIdempotencia");
        validarHash(hashParametros, "hashParametros");
        Objects.requireNonNull(creadoEn, "La fecha de auditoria es obligatoria.");
    }

    private static String normalizarOperacion(String tipo) {
        Objects.requireNonNull(tipo, "El tipo de operacion es obligatorio.");
        String normalizado = tipo.trim().toUpperCase(Locale.ROOT);
        if (!"PURCHASE".equals(normalizado) && !"SALE".equals(normalizado)) {
            throw new IllegalArgumentException("El tipo de operacion no es valido.");
        }
        return normalizado;
    }

    private static String normalizarMotivo(String motivo) {
        Objects.requireNonNull(motivo, "El codigo de motivo es obligatorio.");
        String normalizado = motivo.trim().toUpperCase(Locale.ROOT);
        try {
            TokenMarketException.Reason.valueOf(normalizado);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("El codigo de motivo no es valido.", exception);
        }
        return normalizado;
    }

    private static void validarHash(String hash, String nombre) {
        if (hash != null && !SHA_256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException(nombre + " debe ser un SHA-256 hexadecimal.");
        }
    }
}
