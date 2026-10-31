package com.devchesterunited.devchesterunitedv2.controller.market;

import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2POfferRequestDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2POfferResponseDTO;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** HTTP boundary for publishing P2P offers. */
@RestController
@RequestMapping("/api/v1/market/p2p")
public class MarketP2POfferController {
    private final MarketP2POfferService offers;

    public MarketP2POfferController(MarketP2POfferService offers) {
        this.offers = offers;
    }

    @PostMapping("/offers")
    @Operation(operationId = "publicarMarketP2POffer",
            summary = "Publicar una oferta P2P",
            description = "Reserva tokens disponibles usando la cotizacion oficial; no admite precio manual.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Oferta creada o replay idempotente.",
            content = @Content(schema = @Schema(implementation = MarketP2POfferResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Cantidad o precio manual invalidos.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Jugador, cotizacion, idempotencia o disponibilidad en conflicto.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Mercado no operativo.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketP2POfferResponseDTO>> publish(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    description = "Clave nueva por operacion logica; maximo 200 caracteres.",
                    schema = @Schema(type = "string", minLength = 1, maxLength = 200))
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody MarketP2POfferRequestDTO request) {
        UUID usuarioId = usuarioDesde(jwt);
        request.validarCamposAdicionales();
        String clave = normalizeIdempotencyKey(idempotencyKey);
        var result = offers.publicar(request.toCommand(usuarioId, clave));
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketP2POfferResponseDTO.fromDomain(result)));
    }

    private static UUID usuarioDesde(Jwt jwt) {
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
