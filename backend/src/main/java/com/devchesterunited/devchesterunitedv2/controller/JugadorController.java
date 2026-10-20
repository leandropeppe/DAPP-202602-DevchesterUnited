package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.controller.dto.FiltroJugadoresDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.EstadisticasJugadorResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.JugadorDetalleResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.JugadoresResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.ValuacionHistoryResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.ValuacionResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.CotizacionConsultaService;
import com.devchesterunited.devchesterunitedv2.service.dto.EstadisticasJugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadoresResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.ValuacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.ValuacionHistoricoDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class JugadorController {
    private static final Set<String> PARAMETROS_PERMITIDOS =
            Set.of("league", "team", "position", "page", "limit");

    private final JugadorService servicio;
    private final CotizacionConsultaService cotizacionConsulta;

    public JugadorController(JugadorService servicio, CotizacionConsultaService cotizacionConsulta) {
        this.servicio = servicio;
        this.cotizacionConsulta = cotizacionConsulta;
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
    @GetMapping("/players/{playerId}/valuation/history")
    public ResponseEntity<SuccessResponseDTO<ValuacionHistoryResponseDTO>> consultarHistorico(
            @PathVariable String playerId,
            @RequestParam(name = "strategyId", required = false) String strategyId,
            @RequestParam(name = "strategyVersion", required = false) Integer strategyVersion,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(new SuccessResponseDTO<>(mapear(cotizacionConsulta.consultarHistorico(
                parsearIdPublico(playerId), strategyId, strategyVersion, page, limit))));
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

    private static JugadoresResponseDTO mapear(JugadoresResultado resultado) {
        return new JugadoresResponseDTO(
                resultado.jugadores().stream().map(JugadorController::mapear).toList(),
                new com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO(
                        resultado.paginacion().paginaActual(), resultado.paginacion().tamanoPagina(),
                        resultado.paginacion().totalPaginas(), resultado.paginacion().totalRegistros()));
    }

    private static com.devchesterunited.devchesterunitedv2.controller.dto.JugadorResponseDTO mapear(JugadorResultado jugador) {
        return new com.devchesterunited.devchesterunitedv2.controller.dto.JugadorResponseDTO(
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
