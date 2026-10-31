package com.devchesterunited.devchesterunitedv2.controller.market;

import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2POfferListResponseDTO;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferQuery;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferQueryService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** HTTP boundary for the public, authenticated P2P offer book. */
@RestController
@RequestMapping("/api/v1/market/p2p")
public class MarketP2POfferQueryController {
    private final MarketP2POfferQueryService queries;

    public MarketP2POfferQueryController(MarketP2POfferQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/offers")
    @Operation(operationId = "listarMarketP2POffers", summary = "Listar ofertas P2P",
            description = "Devuelve ofertas con cotizacion oficial y alias publico del vendedor.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Listado paginado de ofertas P2P.",
            content = @Content(schema = @Schema(implementation = MarketP2POfferListResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Filtros o paginacion invalidos.")
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.")
    public ResponseEntity<SuccessResponseDTO<MarketP2POfferListResponseDTO>> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(name = "playerId", in = ParameterIn.QUERY,
                    description = "Identificador UUID del jugador.",
                    schema = @Schema(type = "string", format = "uuid"))
            @RequestParam(required = false) String playerId,
            @Parameter(name = "status", in = ParameterIn.QUERY,
                    description = "Estado persistido o derivado de la oferta.",
                    schema = @Schema(type = "string", allowableValues = {
                            "OPEN", "PARTIALLY_FILLED", "PURCHASABLE", "SUSPENDED", "FILLED", "CANCELLED"}))
            @RequestParam(required = false) String status,
            @Parameter(name = "mine", in = ParameterIn.QUERY,
                    description = "Filtra por ofertas del usuario autenticado.",
                    schema = @Schema(type = "boolean", defaultValue = "false"))
            @RequestParam(defaultValue = "false") boolean mine,
            @Parameter(name = "page", in = ParameterIn.QUERY,
                    description = "Pagina desde 1.",
                    schema = @Schema(type = "integer", minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1") int page,
            @Parameter(name = "limit", in = ParameterIn.QUERY,
                    description = "Registros por pagina, entre 1 y 100.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int limit) {
        UUID userId = userIdFrom(jwt);
        UUID parsedPlayer = parsePlayerId(playerId);
        var result = queries.consultar(buildQuery(userId, parsedPlayer, status, mine, page, limit));
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketP2POfferListResponseDTO.fromDomain(result)));
    }

    private static UUID userIdFrom(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || !jwt.getSubject().startsWith("usr_")) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Credenciales invalidas.");
        }
        try {
            return UUID.fromString(jwt.getSubject().substring(4));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Credenciales invalidas.");
        }
    }

    private static UUID parsePlayerId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El playerId no es valido.");
        }
    }

    private static MarketP2POfferQuery buildQuery(UUID userId, UUID playerId, String status,
            boolean mine, int page, int limit) {
        try {
            return new MarketP2POfferQuery(userId, playerId, status, mine, page, limit);
        } catch (IllegalArgumentException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    exception.getMessage());
        }
    }
}
