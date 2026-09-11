package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.controller.dto.FiltroJugadoresDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.JugadoresResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.JugadorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class JugadorController {
    private static final Set<String> PARAMETROS_PERMITIDOS =
            Set.of("league", "team", "position", "page", "limit");

    private final JugadorService servicio;

    public JugadorController(JugadorService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/players")
    public ResponseEntity<SuccessResponseDTO<JugadoresResponseDTO>> consultar(
            @RequestParam(name = "league", required = false) String league,
            @RequestParam(name = "team", required = false) String team,
            @RequestParam(name = "position", required = false) String position,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "limit", required = false) String limit,
            HttpServletRequest request) {

        validarParametros(request);

        FiltroJugadoresDTO filtros =
                FiltroJugadoresDTO.desdeParametros(league, team, position, page, limit);

        JugadoresResponseDTO respuesta = servicio.consultar(filtros);
        return ResponseEntity.ok(new SuccessResponseDTO<>(respuesta));
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

    private static ConstraintViolationException invalido(String mensaje) {
        return new ConstraintViolationException(mensaje, Set.of());
    }
}
