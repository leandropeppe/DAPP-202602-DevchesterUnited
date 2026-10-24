package com.devchesterunited.devchesterunitedv2.controller.market;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;

import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketBalanceResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPlayerResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPlayersResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketOperationResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPurchaseRequestDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPurchaseResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketSaleRequestDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketSaleResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketPortfolioResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketTransactionsResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketAvailabilityResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPlayerAvailability;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPortfolioService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketTransactionService;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
    private final MarketBalanceService balances;
    private final MarketAvailabilityService availability;
    private final MarketIdempotencyService idempotency;
    private final MarketPurchaseService purchases;
    private final MarketSaleService sales;
    private final MarketPortfolioService portfolios;
    private final MarketTransactionService transactions;

    @Autowired
    public MarketController(MarketBalanceService balances, MarketAvailabilityService availability,
            MarketIdempotencyService idempotency, MarketPurchaseService purchases,
            MarketSaleService sales, MarketPortfolioService portfolios,
            MarketTransactionService transactions) {
        this.balances = balances;
        this.availability = availability;
        this.idempotency = idempotency;
        this.purchases = purchases;
        this.sales = sales;
        this.portfolios = portfolios;
        this.transactions = transactions;
    }

    @GetMapping("/portfolio")
    @Operation(operationId = "consultarMarketPortfolio", summary = "Consultar portfolio de mercado",
            description = "Devuelve las posiciones y rentabilidad del usuario autenticado.",
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
                    + "El filtro from es inclusivo y to es exclusivo.",
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
                    description = "Tipo de operacion: PURCHASE o SALE.",
                    schema = @Schema(type = "string", allowableValues = {"PURCHASE", "SALE"}))
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
            description = "Ejecuta una venta atomica usando la cotizacion exacta indicada por el usuario.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Venta confirmada o solicitud pendiente.",
            content = @Content(schema = @Schema(implementation = MarketSaleResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud o Idempotency-Key invalida.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "Cuenta de mercado inexistente.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Cotizacion, tenencia, reserva o idempotencia en conflicto.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Mercado no operativo.",
            content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    public ResponseEntity<SuccessResponseDTO<MarketSaleResponseDTO>> sale(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MarketSaleRequestDTO request) {
        UUID usuarioId = usuarioDesde(jwt);
        String clave = normalizeIdempotencyKey(idempotencyKey);
        var result = sales.vender(request.toDomain(usuarioId, clave));
        return ResponseEntity.ok(new SuccessResponseDTO<>(
                MarketSaleResponseDTO.fromDomain(result)));
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
