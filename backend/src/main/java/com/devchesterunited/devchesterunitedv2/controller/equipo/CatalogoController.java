package com.devchesterunited.devchesterunitedv2.controller.equipo;

import com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.estadisticas.dto.SincronizacionResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SincronizacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionCatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogoController {
    private final SincronizacionCatalogoService servicio;

    public CatalogoController(SincronizacionCatalogoService servicio) {
        this.servicio = servicio;
    }

    @Operation(operationId = "sincronizarCatalogo", summary = "Sincronizar las cinco ligas con la temporada vigente del proveedor",
            description = "Sin cuerpo ni parametros. Requiere API Key administrativa. Solo una sincronizacion global puede ejecutarse a la vez.",
            security = @SecurityRequirement(name = "ApiKeyAuth"))
        @ApiResponse(responseCode = "200", description = "Snapshot publicado atomicamente.")
        @ApiResponse(responseCode = "400", description = "Solicitud autenticada con cuerpo no vacio.", content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
        @ApiResponse(responseCode = "401", description = "API Key ausente, invalida o usuario inexistente.", content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
        @ApiResponse(responseCode = "409", description = "Sincronizacion global en curso.", content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
        @ApiResponse(responseCode = "503", description = "Proveedor, cuota, timeout o BD no disponibles.", content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
        @ApiResponse(responseCode = "500", description = "Fallo inesperado.", content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PostMapping("/sync")
    public ResponseEntity<SuccessResponseDTO<SincronizacionResponseDTO>> sincronizar(
            @AuthenticationPrincipal UUID usuario,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @RequestBody(required = false) String cuerpo) {
        if (cuerpo != null && !cuerpo.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "La solicitud no es valida.");
        }
        servicio.validarUsuario(usuario);
        var resultado = servicio.sincronizar();
        return ResponseEntity.ok(new SuccessResponseDTO<>("Catalogo sincronizado correctamente", aResponse(resultado)));
    }

    private static SincronizacionResponseDTO aResponse(SincronizacionResultado r) {
        return new SincronizacionResponseDTO(r.equiposProcesados(), r.jugadoresProcesados(), r.equiposCreados(),
                r.equiposActualizados(), r.jugadoresCreados(), r.jugadoresActualizados());
    }
}
