package com.devchesterunited.devchesterunitedv2.controller.market;

import com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.*;
import com.devchesterunited.devchesterunitedv2.exception.JugadorNoEncontradoException;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketAvailabilityResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPlayerAvailability;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
    private final MarketBalanceService balances;
    private final MarketAvailabilityService availability;
    private final MarketIdempotencyService idempotency;
    private final MarketPurchaseService purchases;
    private final MarketPortfolioService portfolios;
    private final MarketTransactionService transactions;

    @Autowired
    public MarketController(MarketBalanceService balances, MarketAvailabilityService availability,
            MarketIdempotencyService idempotency, MarketPurchaseService purchases,
            MarketPortfolioService portfolios, MarketTransactionService transactions) {
        this.balances = balances;
        this.availability = availability;
        this.idempotency = idempotency;
        this.purchases = purchases;
        this.portfolios = portfolios;
        this.transactions = transactions;
    }

    @GetMapping("/portfolio")
    @Operation(operationId = "consultarMarketPortfolio", summary = "Consultar portfolio de mercado",
            description = "Devuelve las posiciones y rentabilidad del usuario autenticado. Cada posición incluye "
                    + "total_quantity, reserved_quantity y available_quantity, donde available_quantity = "
                    + "total_quantity - reserved_quantity. La identidad se deriva del JWT.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Portfolio del usuario autenticado.",
            content = @Content(schema = @Schema(implementation = MarketPortfolioResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketPortfolioResponseDTO>> portfolio(
            @AuthenticationPrincipal Jwt jwt) {
        UUID usuarioId = usuarioDesde(jwt);
        var result = portfolios.consultar(usuarioId);
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketPortfolioResponseDTO.fromDomain(result)));
    }

    @GetMapping("/transactions")
    @Operation(operationId = "consultarMarketTransactions",
            summary = "Consultar historial de transacciones",
            description = "Devuelve solo transacciones confirmadas del usuario autenticado. "
                    + "El filtro from es inclusivo y to es exclusivo. Los tipos son "
                    + "PRIMARY_PURCHASE, SALE legacy, P2P_PURCHASE y P2P_SALE. El orden es "
                    + "confirmed_at DESC, transaction_id DESC.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Historial confirmado del usuario autenticado.",
            content = @Content(schema = @Schema(implementation = MarketTransactionsResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Filtros o paginacion invalidos.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketTransactionsResponseDTO>> transactions(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(name = "playerId", in = ParameterIn.QUERY,
                    description = "Identificador UUID del jugador.",
                    schema = @Schema(type = "string", format = "uuid"))
            @RequestParam(required = false) String playerId,
            @Parameter(name = "type", in = ParameterIn.QUERY,
                    description = "Tipo: PRIMARY_PURCHASE, SALE legacy, P2P_PURCHASE o P2P_SALE. "
                            + "PURCHASE se conserva como alias compatible de PRIMARY_PURCHASE.",
                    schema = @Schema(type = "string", allowableValues = {"PRIMARY_PURCHASE", "PURCHASE",
                            "SALE", "P2P_PURCHASE", "P2P_SALE"}))
            @RequestParam(required = false) String type,
            @Parameter(name = "from", in = ParameterIn.QUERY,
                    description = "Instante ISO-8601 inclusivo.",
                    schema = @Schema(type = "string", format = "date-time"))
            @RequestParam(required = false) String from,
            @Parameter(name = "to", in = ParameterIn.QUERY,
                    description = "Instante ISO-8601 exclusivo.",
                    schema = @Schema(type = "string", format = "date-time"))
            @RequestParam(required = false) String to,
            @Parameter(name = "page", in = ParameterIn.QUERY,
                    description = "Pagina desde 1.",
                    schema = @Schema(type = "integer", minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1") String page,
            @Parameter(name = "limit", in = ParameterIn.QUERY,
                    description = "Registros por pagina, entre 1 y 100.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") String limit) {
        UUID usuarioId = usuarioDesde(jwt);
        MarketTransactionFilter filtro = transactionFilter(playerId, type, from, to, page, limit);
        var result = transactions.consultar(usuarioId, filtro);
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketTransactionsResponseDTO.fromDomain(result)));
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

    @GetMapping("/players/{playerId}")
    @Operation(operationId = "consultarMarketPlayer", summary = "Consultar disponibilidad de un jugador en el mercado",
            description = "Devuelve la disponibilidad y cotizacion del jugador indicado.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Disponibilidad del jugador.")
    @ApiResponse(responseCode = "404", description = "Jugador inexistente.")
    @ApiResponse(responseCode = "503", description = "Mercado no operativo.")
    public ResponseEntity<SuccessResponseDTO<MarketPlayerResponseDTO>> player(
            @Parameter(name = "playerId", in = ParameterIn.PATH, required = true,
                    description = "Identificador UUID del jugador.",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID playerId) {
        var resultado = availability.consultarJugador(playerId)
                .orElseThrow(JugadorNoEncontradoException::new);
        return ResponseEntity.ok(new SuccessResponseDTO<>(mapear(resultado)));
    }

    @PostMapping("/purchases")
    @Operation(operationId = "comprarTokens", summary = "Comprar tokens de un jugador",
            description = "Ejecuta una compra atomica usando la cotizacion exacta indicada por el usuario.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Compra confirmada o solicitud pendiente.",
            content = @Content(schema = @Schema(implementation = MarketPurchaseResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud o Idempotency-Key invalida.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "Cuenta de mercado inexistente.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Cotizacion, saldo, inventario o idempotencia en conflicto.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Mercado no operativo.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketPurchaseResponseDTO>> purchase(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MarketPurchaseRequestDTO request) {
        UUID usuarioId = usuarioDesde(jwt);
        String clave = normalizeIdempotencyKey(idempotencyKey);
        var result = purchases.comprar(request.toDomain(usuarioId, clave));
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketPurchaseResponseDTO.fromDomain(result)));
    }

    @PostMapping("/sales")
    @Operation(operationId = "venderTokens", summary = "Vender tokens de un jugador",
            description = "La venta nueva al superusuario fue retirada. El historial de ventas se conserva.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "410", description = "La venta nueva al superusuario fue retirada.")
    public ResponseEntity<SuccessResponseDTO<MarketSaleResponseDTO>> sale(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MarketSaleRequestDTO request) {
        usuarioDesde(jwt);
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @GetMapping("/operations/{idempotency-key}")
    @Operation(operationId = "recuperarMarketOperation", summary = "Recuperar una operacion del mercado",
            description = "Devuelve el estado de una solicitud del usuario autenticado. Los estados pendientes o inciertos no son compraventas confirmadas.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Estado de la solicitud.",
            content = @Content(schema = @Schema(implementation = MarketOperationResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Clave de idempotencia invalida.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "Solicitud inexistente para el usuario autenticado.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketOperationResponseDTO>> operation(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(name = "idempotency-key", in = ParameterIn.PATH,
                    description = "Clave de idempotencia de la solicitud.", required = true,
                    schema = @Schema(type = "string", maxLength = 200, minLength = 1))
            @org.springframework.web.bind.annotation.PathVariable("idempotency-key") String idempotencyKey) {
        UUID usuarioId = usuarioDesde(jwt);
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La clave de idempotencia no es valida.");
        }
        MarketIdempotencyResult result = idempotency.recuperar(usuarioId, idempotencyKey.trim())
                .orElseThrow(() -> new TokenMarketException(TokenMarketException.Reason.OPERATION_NOT_FOUND,
                        "La operacion no existe."));
        return ResponseEntity.ok(new SuccessResponseDTO<>(toPublicOperation(result)));
    }

    private static MarketOperationResponseDTO toPublicOperation(MarketIdempotencyResult result) {
        UUID publicResultReference = result.estado() == com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus.CONFIRMED
                ? result.referenciaResultado() : null;
        return new MarketOperationResponseDTO(result.claveIdempotencia(), result.tipoOperacion(),
                result.estado().name(), publicResultReference, result.actualizadoEn());
    }

    private static MarketPlayerResponseDTO mapear(MarketPlayerAvailability jugador) {
        return new MarketPlayerResponseDTO(
                "ply_" + jugador.playerId(), jugador.playerName(), jugador.league(), jugador.teamName(),
                jugador.playerActive(), jugador.teamActive(), jugador.inventoryQuantity(), jugador.operable(),
                jugador.quoteId() == null ? null : jugador.quoteId().toString(), jugador.quoteVersion(),
                jugador.pricePerToken(), jugador.validityStatus(), jugador.quoteAvailability().name(),
                jugador.validFrom(), jugador.validUntil());
    }

    private static MarketTransactionFilter transactionFilter(String playerId, String type,
            String from, String to, String page, String limit) {
        UUID jugador = optionalUuid(playerId, "playerId");
        Instant desde = optionalInstant(from, "from");
        Instant hasta = optionalInstant(to, "to");
        int pagina = requiredInteger(page, "page");
        int limite = requiredInteger(limit, "limit");
        try {
            return new MarketTransactionFilter(jugador, optionalText(type), desde, hasta,
                    pagina, limite);
        } catch (IllegalArgumentException exception) {
            throw invalidTransactionFilter();
        }
    }

    private static UUID optionalUuid(String value, String parameter) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw invalidTransactionFilter(parameter);
        }
    }

    private static Instant optionalInstant(String value, String parameter) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalidTransactionFilter(parameter);
        }
    }

    private static int requiredInteger(String value, String parameter) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException exception) {
            throw invalidTransactionFilter(parameter);
        }
    }

    private static String optionalText(String value) {
        return value == null ? null : value.trim();
    }

    private static TokenMarketException invalidTransactionFilter(String parameter) {
        return new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                "El filtro " + parameter + " no es valido.");
    }

    private static TokenMarketException invalidTransactionFilter() {
        return new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                "Los filtros del historial no son validos.");
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

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La clave de idempotencia no es valida.");
        }
        return idempotencyKey.trim();
    }

    private static void exigirMercadoOperativo(boolean operativa) {
        if (!operativa) {
            throw new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                    "El mercado no esta operativo.");
        }
    }
}
