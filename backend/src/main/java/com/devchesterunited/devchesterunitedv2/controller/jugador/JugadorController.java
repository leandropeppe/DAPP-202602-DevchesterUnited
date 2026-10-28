package com.devchesterunited.devchesterunitedv2.controller.jugador;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionScheduler;
import com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.estadisticas.dto.EstadisticasJugadorResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.jugador.dto.*;
import com.devchesterunited.devchesterunitedv2.controller.valorizacion.dto.ValuacionHistoryResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.valorizacion.dto.ValuacionResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.EstadisticasJugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorRankingResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadoresResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.valorizacion.ValuacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.valorizacion.ValuacionHistoricoDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.simulacion.SimulacionCotizacionService;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionCotizacionRequestDTO;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CodigoErrorSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ExcepcionSimulacion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.CotizacionConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class JugadorController {
    private static final Set<String> PARAMETROS_PERMITIDOS =
            Set.of("league", "team", "position", "page", "limit");

    private final JugadorService servicio;
    private final CotizacionConsultaService cotizacionConsulta;
    private final ValorizacionScheduler valorizacionScheduler;
    private final SimulacionCotizacionService simulacionService;

    public JugadorController(JugadorService servicio, CotizacionConsultaService cotizacionConsulta,
            ValorizacionScheduler valorizacionScheduler, SimulacionCotizacionService simulacionService) {
        this.servicio = servicio;
        this.cotizacionConsulta = cotizacionConsulta;
        this.valorizacionScheduler = valorizacionScheduler;
        this.simulacionService = simulacionService;
    }

    @Operation(
            operationId = "consultarJugadores",
            summary = "Consultar catálogo con filtros parciales",
            description = "Consulta pública local, con filtros parciales case-insensitive y paginación."
            )
        @ApiResponse(
                responseCode = "200",
                description = "Jugadores y metadatos de paginación.")
        @ApiResponse(
                responseCode = "400",
                description = "Parámetros de consulta inválidos.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponseDTO.class)))
        @ApiResponse(
                responseCode = "500",
                description = "Fallo interno; mensaje genérico en español.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponseDTO.class)))
        @ApiResponse(
                responseCode = "503",
                description = "Base de datos no disponible; no se concede acceso.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponseDTO.class)))
    @GetMapping("/players")
    public ResponseEntity<SuccessResponseDTO<JugadoresResponseDTO>> consultar(
            @Parameter(
                    name = "league",
                    in = ParameterIn.QUERY,
                    description = "Subcadena literal case-insensitive.",
                    schema = @Schema(type = "string", minLength = 1, maxLength = 100))
            @RequestParam(name = "league", required = false) String league,
            @Parameter(
                    name = "team",
                    in = ParameterIn.QUERY,
                    description = "Subcadena literal case-insensitive.",
                    schema = @Schema(type = "string", minLength = 1, maxLength = 100))
            @RequestParam(name = "team", required = false) String team,
            @Parameter(
                    name = "position",
                    in = ParameterIn.QUERY,
                    description = "Subcadena literal case-insensitive.",
                    schema = @Schema(type = "string", minLength = 1, maxLength = 30))
            @RequestParam(name = "position", required = false) String position,
            @Parameter(
                    name = "page",
                    in = ParameterIn.QUERY,
                    description = "Número de página desde 1.",
                    schema = @Schema(
                            type = "integer",
                            format = "int32",
                            minimum = "1",
                            maximum = "2147483647",
                            defaultValue = "1"))
            @RequestParam(name = "page", required = false) String page,
            @Parameter(
                    name = "limit",
                    in = ParameterIn.QUERY,
                    description = "Cantidad de registros por página.",
                    schema = @Schema(
                            type = "integer",
                            minimum = "1",
                            maximum = "100",
                            defaultValue = "20"))
            @RequestParam(name = "limit", required = false) String limit,
            HttpServletRequest request) {

        validarParametros(request);

        FiltroJugadoresDTO filtros =
                FiltroJugadoresDTO.desdeParametros(league, team, position, page, limit);

        JugadoresResponseDTO respuesta = mapear(servicio.consultar(filtros));
        return ResponseEntity.ok(new SuccessResponseDTO<>(respuesta));
    }

    @Operation(
            operationId = "consultarDetalleJugador",
            summary = "Consultar detalle de jugador y estadisticas persistidas")
    @ApiResponse(responseCode = "200", description = "Detalle del jugador.")
    @ApiResponse(responseCode = "404", description = "Jugador no encontrado.")
    @GetMapping("/players/{id}")
    public ResponseEntity<SuccessResponseDTO<JugadorDetalleResponseDTO>> consultarDetalle(
            @PathVariable String id) {
        JugadorDetalleResponseDTO respuesta = mapearDetalle(servicio.consultarDetalle(id));
        return ResponseEntity.ok(new SuccessResponseDTO<>(respuesta));
    }

    @Operation(operationId = "obtenerConfiguracionSimulador", summary = "Obtener configuración del simulador")
    @ApiResponse(responseCode = "200", description = "Configuración editable del simulador.")
    @ApiResponse(responseCode = "404", description = "Jugador no encontrado.")
    @ApiResponse(responseCode = "409", description = "Jugador no simulable.")
    @GetMapping("/players/{playerId}/valuation-simulator")
    public ResponseEntity<SuccessResponseDTO<SimulacionConfiguracionHttpDTO>> obtenerConfiguracionSimulador(
            @PathVariable String playerId) {
        return ResponseEntity.ok(new SuccessResponseDTO<>(SimulacionConfiguracionHttpDTO.from(
                simulacionService.obtenerConfiguracion(playerId))));
    }

    @Operation(operationId = "simularCotizacion", summary = "Simular cotización sin persistir")
    @ApiResponse(responseCode = "200", description = "Resultado transitorio de la simulación.")
    @ApiResponse(responseCode = "400", description = "Request o escenario inválido.")
    @ApiResponse(responseCode = "404", description = "Jugador no encontrado.")
    @ApiResponse(responseCode = "409", description = "Jugador no simulable.")
    @ApiResponse(responseCode = "401", description = "JWT ausente o invalido.")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/players/{playerId}/valuation-simulator")
    public ResponseEntity<SuccessResponseDTO<SimulacionResultadoHttpDTO>> simularCotizacion(
            @PathVariable String playerId, @jakarta.validation.Valid @RequestBody SimulacionCotizacionRequestHttpDTO request) {
        if (!request.horizontePermitido()) {
            throw new ExcepcionSimulacion(CodigoErrorSimulacion.HORIZONTE_INVALIDO,
                    "horizonMatches debe ser 3, 5, 8 o 10.");
        }
        SimulacionCotizacionRequestDTO internal = new SimulacionCotizacionRequestDTO(
                request.horizonMatches(), request.modifications());
        return ResponseEntity.ok(new SuccessResponseDTO<>(SimulacionResultadoHttpDTO.from(
                simulacionService.simular(playerId, internal))));
    }

    @Operation(operationId = "consultarRankingJugadores", summary = "Consultar ranking de jugadores",
            description = "Devuelve los jugadores ordenados por cotización persistida de la estrategia activa.")
    @ApiResponse(responseCode = "200", description = "Ranking ordenado de mayor a menor cotización.")
    @ApiResponse(responseCode = "400", description = "Límite inválido.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    @GetMapping("/players/ranking")
    public ResponseEntity<SuccessResponseDTO<RankingJugadoresResponseDTO>> consultarRanking(
            @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Cantidad máxima de jugadores.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) {
            throw invalido("El límite no es válido.");
        }
        return ResponseEntity.ok(new SuccessResponseDTO<>(new RankingJugadoresResponseDTO(
                servicio.consultarRanking(limit).stream().map(JugadorController::mapear).toList())));
    }

    @Operation(operationId = "consultarCotizacionActualJugador",
            summary = "Consultar cotización actual de un jugador")
    @ApiResponse(responseCode = "200", description = "Cotización actual de la estrategia seleccionada.")
    @ApiResponse(responseCode = "400", description = "Identificador o estrategia inválidos.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "Jugador o cotización no encontrada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    @GetMapping("/players/{playerId}/valuation")
    public ResponseEntity<SuccessResponseDTO<ValuacionResponseDTO>> consultarCotizacionActual(
            @PathVariable String playerId,
            @RequestParam(name = "strategyId", required = false) String strategyId,
            @RequestParam(name = "strategyVersion", required = false) Integer strategyVersion) {
        return ResponseEntity.ok(new SuccessResponseDTO<>(mapear(cotizacionConsulta.consultarActual(
                parsearIdPublico(playerId), strategyId, strategyVersion))));
    }

    @Operation(operationId = "consultarHistoricoCotizacionJugador",
            summary = "Consultar histórico de cotización de un jugador")
    @ApiResponse(responseCode = "200", description = "Histórico más reciente al más antiguo.")
    @ApiResponse(responseCode = "400", description = "Identificador, estrategia o paginación inválidos.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "Jugador o histórico no encontrado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    @GetMapping("/players/{playerId}/quotes")
    public ResponseEntity<SuccessResponseDTO<ValuacionHistoryResponseDTO>> consultarHistorico(
            @PathVariable String playerId,
            @RequestParam(name = "strategyId", required = false) String strategyId,
            @RequestParam(name = "strategyVersion", required = false) Integer strategyVersion,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(new SuccessResponseDTO<>(mapear(cotizacionConsulta.consultarHistorico(
                parsearIdPublico(playerId), strategyId, strategyVersion, page, limit))));
    }

    @Operation(operationId = "recalcularCotizaciones", summary = "Recalcular cotizaciones",
            description = "Ejecuta manualmente el mismo caso de uso y la misma configuración de elegibilidad del scheduler.",
            security = @SecurityRequirement(name = "ApiKeyAuth"))
    @ApiResponse(responseCode = "200", description = "Recalculo ejecutado correctamente.")
    @ApiResponse(responseCode = "401", description = "API Key ausente o inválida.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PostMapping("/quotes/recalculate")
    public ResponseEntity<SuccessResponseDTO<String>> recalcularCotizaciones() {
        valorizacionScheduler.ejecutarSemanal();
        return ResponseEntity.ok(new SuccessResponseDTO<>("Cotizaciones recalculadas correctamente"));
    }

    private static ValuacionResponseDTO mapear(ValuacionDTO valuacion) {
        return new ValuacionResponseDTO(valuacion.playerId(), valuacion.score(), valuacion.value(),
                valuacion.calculatedAt(), valuacion.strategyId(), valuacion.strategyVersion(), valuacion.visible(),
                valuacion.strategyType(), valuacion.position(), valuacion.trace());
    }

    private static ValuacionHistoryResponseDTO mapear(ValuacionHistoricoDTO historico) {
        return new ValuacionHistoryResponseDTO(historico.items().stream().map(JugadorController::mapear).toList(),
                historico.page(), historico.limit(), historico.total());
    }

    private static JugadorRankingResponseDTO mapear(JugadorRankingResultado jugador) {
        return new JugadorRankingResponseDTO(jugador.rank(), "ply_" + jugador.playerId(), jugador.nombre(),
                jugador.posicion(), jugador.equipo(), jugador.cotizacion(), jugador.strategyId(),
                jugador.strategyVersion());
    }

    private static JugadoresResponseDTO mapear(JugadoresResultado resultado) {
        return new JugadoresResponseDTO(
                resultado.jugadores().stream().map(JugadorController::mapear).toList(),
                new com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO(
                        resultado.paginacion().paginaActual(), resultado.paginacion().tamanoPagina(),
                        resultado.paginacion().totalPaginas(), resultado.paginacion().totalRegistros()));
    }

    private static com.devchesterunited.devchesterunitedv2.controller.jugador.dto.JugadorResponseDTO mapear(JugadorResultado jugador) {
        return new com.devchesterunited.devchesterunitedv2.controller.jugador.dto.JugadorResponseDTO(
                "ply_" + jugador.id(), jugador.nombre(), jugador.liga(), jugador.equipo(), jugador.posicion(),
                jugador.dorsal(), jugador.nacionalidad(), jugador.valuation());
    }

    private static JugadorDetalleResponseDTO mapearDetalle(JugadorDetalleResultado jugador) {
        return new JugadorDetalleResponseDTO(
                "ply_" + jugador.id(),
                jugador.nombre(),
                jugador.liga(),
                jugador.equipo(),
                jugador.posicion(),
                jugador.dorsal(),
                jugador.nacionalidad(),
                mapearEstadisticas(jugador.estadisticas()),
                jugador.valuation());
    }

    private static EstadisticasJugadorResponseDTO mapearEstadisticas(
            EstadisticasJugadorResultado estadisticas) {
        if (estadisticas == null) {
            return null;
        }
        return new EstadisticasJugadorResponseDTO(
                estadisticas.posicion(),
                estadisticas.minutosJugados(),
                estadisticas.goles(),
                estadisticas.asistencias(),
                estadisticas.tiros(),
                estadisticas.pasesClave(),
                estadisticas.regates(),
                estadisticas.tackles(),
                estadisticas.valoracion(),
                estadisticas.tarjetasAmarillas(),
                estadisticas.tarjetasRojas(),
                estadisticas.generales() == null ? null : new EstadisticasJugadorResponseDTO.Generales(
                        estadisticas.generales().apps(), estadisticas.generales().subOn(), estadisticas.generales().minsPlayed(),
                        estadisticas.generales().manOfTheMatch(), estadisticas.generales().aerialWonPerGame(), estadisticas.generales().rating(),
                        estadisticas.generales().yellowCard(), estadisticas.generales().redCard()),
                estadisticas.ofensivas() == null ? null : new EstadisticasJugadorResponseDTO.Ofensivas(
                        estadisticas.ofensivas().goal(), estadisticas.ofensivas().assistTotal(), estadisticas.ofensivas().shotsPerGame(),
                        estadisticas.ofensivas().keyPassPerGame(), estadisticas.ofensivas().dribbleWonPerGame(), estadisticas.ofensivas().foulGivenPerGame(),
                        estadisticas.ofensivas().offsideGivenPerGame(), estadisticas.ofensivas().dispossessedPerGame(), estadisticas.ofensivas().turnoverPerGame()),
                estadisticas.defensivas() == null ? null : new EstadisticasJugadorResponseDTO.Defensivas(
                        estadisticas.defensivas().tacklePerGame(), estadisticas.defensivas().interceptionPerGame(), estadisticas.defensivas().foulsPerGame(),
                        estadisticas.defensivas().offsideWonPerGame(), estadisticas.defensivas().clearancePerGame(), estadisticas.defensivas().wasDribbledPerGame(),
                        estadisticas.defensivas().outfielderBlockPerGame(), estadisticas.defensivas().goalOwn()),
                estadisticas.pases() == null ? null : new EstadisticasJugadorResponseDTO.Pases(
                        estadisticas.pases().totalPassesPerGame(), estadisticas.pases().passSuccess(), estadisticas.pases().accurateCrossesPerGame(),
                        estadisticas.pases().accurateLongPassPerGame(), estadisticas.pases().accurateThroughBallPerGame()));
    }

    private static void validarParametros(HttpServletRequest request) {
        for (var entrada : request.getParameterMap().entrySet()) {
            if (!PARAMETROS_PERMITIDOS.contains(entrada.getKey())) {
                throw invalido("Parámetro de consulta desconocido.");
            }
            if (entrada.getValue().length != 1) {
                throw invalido("No se permiten parámetros repetidos.");
            }
        }
    }

    private static UUID parsearIdPublico(String idPublico) {
        if (idPublico == null || !idPublico.startsWith("ply_")) {
            throw invalido("Identificador de jugador inválido.");
        }
        try {
            return UUID.fromString(idPublico.substring("ply_".length()));
        } catch (IllegalArgumentException exception) {
            throw invalido("Identificador de jugador inválido.");
        }
    }

    private static ConstraintViolationException invalido(String mensaje) {
        return new ConstraintViolationException(mensaje, Set.of());
    }
}
