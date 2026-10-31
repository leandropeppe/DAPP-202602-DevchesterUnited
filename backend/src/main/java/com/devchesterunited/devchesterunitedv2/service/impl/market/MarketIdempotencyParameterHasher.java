package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/** Calcula la huella estable de los parametros funcionales de una operacion. */
public final class MarketIdempotencyParameterHasher {
    private MarketIdempotencyParameterHasher() { }

    public static String hash(MarketIdempotencyRequest request) {
        String operation = normalizeOperation(request.tipoOperacion());
        String canonical = "P2P_OFFER".equals(operation)
                ? offerCanonical(request, operation)
                : primaryCanonical(request, operation);
        return sha256(canonical);
    }

    public static String hash(MarketP2PIdempotencyRequest request) {
        request.validar();
        var identity = request.identidad();
        String canonical = field("P2P_PURCHASE")
                + field(normalizeUuid(identity.offerId()))
                + field(normalizeUuid(identity.playerId()))
                + field(normalizePositive(identity.quantity(), "quantity"))
                + field(normalizeUuid(identity.quote().quoteId()))
                + field(normalizePositive(identity.quote().version(), "quoteVersion"));
        return sha256(canonical);
    }

    public static String hash(MarketP2PCancelCommand request) {
        if (request == null || request.usuarioId() == null || request.offerId() == null
                || request.claveIdempotencia() == null || request.claveIdempotencia().isBlank()) {
            throw new IllegalArgumentException("La solicitud de cancelacion no es valida.");
        }
        String canonical = field("P2P_CANCEL") + field(normalizeUuid(request.offerId()));
        return sha256(canonical);
    }

    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible en la JVM.", exception);
        }
    }

    private static String offerCanonical(MarketIdempotencyRequest request, String operation) {
        return field(operation) + field(normalizeUuid(request.jugadorId()))
                + field(normalizePositive(request.cantidad(), "cantidad"));
    }

    private static String primaryCanonical(MarketIdempotencyRequest request, String operation) {
        return field(operation) + field(normalizeUuid(request.jugadorId()))
                + field(normalizePositive(request.cantidad(), "cantidad"))
                + field(normalizeUuid(request.cotizacionId()))
                + field(normalizePositive(request.versionCotizacion(), "versionCotizacion"));
    }

    private static String field(String value) {
        return value.length() + ":" + value + "|";
    }

    private static String normalizeOperation(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tipoOperacion es obligatorio.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeUuid(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("Los identificadores son obligatorios.");
        }
        return value.toString().toLowerCase(Locale.ROOT);
    }

    private static String normalizePositive(Number value, String name) {
        if (value == null || value.longValue() <= 0) {
            throw new IllegalArgumentException(name + " debe ser positivo.");
        }
        return Long.toString(value.longValue());
    }
}
