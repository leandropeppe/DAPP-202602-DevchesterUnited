package com.devchesterunited.devchesterunitedv2.controller.market;

import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2PPurchaseRequestDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2PPurchaseResponseDTO;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** HTTP boundary for buying a P2P offer. */
@RestController
@RequestMapping("/api/v1/market/p2p/offers")
public class MarketP2PPurchaseController {
    private final MarketP2PPurchaseService purchases;
    private final OfertaVentaP2PRepository offers;

    public MarketP2PPurchaseController(MarketP2PPurchaseService purchases,
            OfertaVentaP2PRepository offers) {
        this.purchases = purchases;
        this.offers = offers;
    }

    @PostMapping("/{offerId}/purchases")
    @Operation(operationId = "comprarMarketP2POffer",
            summary = "Comprar una oferta P2P",
            description = "Liquida una compra usando la cotizacion oficial observada.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Compra confirmada o pendiente.",
            content = @Content(schema = @Schema(implementation = MarketP2PPurchaseResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida.")
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.")
    @ApiResponse(responseCode = "404", description = "Oferta inexistente.")
    @ApiResponse(responseCode = "409", description = "Cotizacion, saldo, cantidad o idempotencia en conflicto.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2PErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Mercado no operativo.")
    public ResponseEntity<SuccessResponseDTO<MarketP2PPurchaseResponseDTO>> purchase(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    description = "Clave nueva por confirmacion; no se reutiliza tras QUOTE_CHANGED.",
                    schema = @Schema(type = "string", minLength = 1, maxLength = 200))
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody MarketP2PPurchaseRequestDTO request) {
        UUID userId = userIdFrom(jwt);
        request.validateFields();
        String key = normalizeIdempotencyKey(idempotencyKey);
        UUID playerId = offers.findById(offerId).map(offer -> offer.getJugadorId())
                .orElseThrow(() -> new TokenMarketException(TokenMarketException.Reason.OFFER_NOT_FOUND,
                        "La oferta no existe."));
        var result = purchases.comprar(new MarketP2PPurchaseCommand(userId, key, offerId,
                playerId, request.getQuantity(),
                new MarketP2PQuoteIdentity(request.getQuoteId(), request.getQuoteVersion())));
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketP2PPurchaseResponseDTO.fromDomain(result)));
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
