package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.controller.dto.MarketBalanceResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.MarketPlayerResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.MarketPlayersResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketAvailabilityResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPlayerAvailability;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketBalanceService;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
    private final MarketBalanceService balances;
    private final MarketAvailabilityService availability;

    public MarketController(MarketBalanceService balances, MarketAvailabilityService availability) {
        this.balances = balances;
        this.availability = availability;
    }

    @GetMapping("/balance")
    @Operation(operationId = "consultarMarketBalance", summary = "Consultar saldo de mercado",
            description = "Devuelve el saldo de creditos de la cuenta del usuario autenticado.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Saldo del usuario autenticado.",
            content = @Content(schema = @Schema(implementation = MarketBalanceResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "Cuenta de mercado inexistente.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Mercado no operativo.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketBalanceResponseDTO>> balance(
            @AuthenticationPrincipal Jwt jwt) {
        UUID usuarioId = usuarioDesde(jwt);
        var resultado = balances.consultar(usuarioId);
        exigirMercadoOperativo(resultado.operativa());
        var saldo = resultado.saldo().orElseThrow(() ->
                new TokenMarketException(TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La cuenta de mercado no existe."));
        return ResponseEntity.ok(new SuccessResponseDTO<>(new MarketBalanceResponseDTO(saldo)));
    }

    @GetMapping("/players")
    @Operation(operationId = "consultarMarketPlayers", summary = "Consultar jugadores operables del mercado",
            description = "Devuelve disponibilidad, tokens y cotizaciones actuales o historicas.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Jugadores del mercado y paginacion.",
            content = @Content(schema = @Schema(implementation = MarketPlayersResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Paginacion invalida.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Estado de mercado incompatible con la consulta.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Mercado no operativo.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketPlayersResponseDTO>> players(
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Pagina desde 1.",
                    schema = @Schema(type = "integer", minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1") int page,
            @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Registros por pagina, entre 1 y 100.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int limit) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La paginacion no es valida.");
        }
        MarketAvailabilityResult resultado = availability.consultarJugadores(page, limit);
        exigirMercadoOperativo(resultado.operativa());
        var data = new MarketPlayersResponseDTO(
                resultado.jugadores().stream().map(MarketController::mapear).toList(),
                new PaginacionDTO(resultado.pagina(), resultado.limite(),
                        resultado.totalPaginas(), resultado.totalRegistros()));
        return ResponseEntity.ok(new SuccessResponseDTO<>(data));
    }

    private static MarketPlayerResponseDTO mapear(MarketPlayerAvailability jugador) {
        return new MarketPlayerResponseDTO(
                "ply_" + jugador.playerId(), jugador.playerName(), jugador.league(), jugador.teamName(),
                jugador.playerActive(), jugador.teamActive(), jugador.inventoryQuantity(), jugador.operable(),
                jugador.quoteId() == null ? null : jugador.quoteId().toString(), jugador.quoteVersion(),
                jugador.pricePerToken(), jugador.validityStatus(), jugador.quoteAvailability().name(),
                jugador.validFrom(), jugador.validUntil());
    }

    private static UUID usuarioDesde(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || !jwt.getSubject().startsWith("usr_")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas.");
        }
        try {
            return UUID.fromString(jwt.getSubject().substring(4));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas.");
        }
    }

    private static void exigirMercadoOperativo(boolean operativa) {
        if (!operativa) {
            throw new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                    "El mercado no esta operativo.");
        }
    }
}
