package com.devchesterunited.devchesterunitedv2.controller.market;

import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2PCancellationResponseDTO;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** HTTP boundary for cancelling a P2P offer. */
@RestController
@RequestMapping("/api/v1/market/p2p/offers")
public class MarketP2PCancellationController {
    private final MarketP2PCancellationService cancellations;

    public MarketP2PCancellationController(MarketP2PCancellationService cancellations) {
        this.cancellations = cancellations;
    }

    @PostMapping("/{offerId}/cancel")
    @Operation(operationId = "cancelarMarketP2POffer",
            summary = "Cancelar una oferta P2P",
            description = "Libera exclusivamente la cantidad restante de la oferta.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Cancelacion confirmada o replay idempotente.",
            content = @Content(schema = @Schema(implementation = MarketP2PCancellationResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida.")
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.")
    @ApiResponse(responseCode = "404", description = "Oferta inexistente o no perteneciente al usuario.")
    @ApiResponse(responseCode = "403", description = "El usuario no puede cancelar la oferta.")
    @ApiResponse(responseCode = "409", description = "Oferta ya completada, cancelada o conflicto de idempotencia.")
    public ResponseEntity<SuccessResponseDTO<MarketP2PCancellationResponseDTO>> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    description = "Clave nueva por operacion logica; maximo 200 caracteres.",
                    schema = @Schema(type = "string", minLength = 1, maxLength = 200))
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {
        UUID userId = userIdFrom(jwt);
        String key = normalizeIdempotencyKey(idempotencyKey);
        var result = cancellations.cancelar(new MarketP2PCancelCommand(userId, offerId, key));
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketP2PCancellationResponseDTO.fromDomain(result)));
    }

    private static UUID userIdFrom(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || !jwt.getSubject().startsWith("usr_")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Credenciales invalidas.");
        }
        try {
            return UUID.fromString(jwt.getSubject().substring(4));
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Credenciales invalidas.");
        }
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La clave de idempotencia no es valida.");
        }
        return value.trim();
    }
}
