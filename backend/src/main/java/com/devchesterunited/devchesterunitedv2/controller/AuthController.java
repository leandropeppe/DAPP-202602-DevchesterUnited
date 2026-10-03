package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.controller.dto.LoginRequestDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.LoginResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.RegistroRequestDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.UsuarioResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.ApiKeyResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.interfaces.AutenticacionService;
import com.devchesterunited.devchesterunitedv2.service.dto.ApiKeyResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.LoginResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.UsuarioResultado;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AutenticacionService servicio;

    public AuthController(AutenticacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/register")
    @Operation(operationId = "registrarUsuario", summary = "Registrar un usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario registrado correctamente."),
        @ApiResponse(responseCode = "400", description = "Datos de registro invÃ¡lidos.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Usuario o correo ya registrados.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Fallo interno.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "503", description = "Base de datos no disponible.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    })
    public ResponseEntity<SuccessResponseDTO<UsuarioResponseDTO>> registrar(
            @Valid @RequestBody RegistroRequestDTO solicitud) {
        UsuarioResponseDTO respuesta = mapear(servicio.registrar(solicitud.nombreUsuario(), solicitud.correo(), solicitud.contrasena()));
        return ResponseEntity.status(201)
                .body(new SuccessResponseDTO<>("Usuario registrado correctamente", respuesta));
    }

    @PostMapping("/login")
    @Operation(operationId = "iniciarSesion", summary = "Iniciar sesiÃ³n")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "AutenticaciÃ³n exitosa."),
        @ApiResponse(responseCode = "400", description = "Credenciales invÃ¡lidas.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "Usuario o contraseÃ±a incorrectos.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Fallo interno.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "503", description = "Base de datos no disponible.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    })
    public ResponseEntity<SuccessResponseDTO<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO solicitud) {
        LoginResponseDTO respuesta = mapear(servicio.login(solicitud.nombreUsuario(), solicitud.contrasena()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new SuccessResponseDTO<>("Autenticación exitosa", respuesta));
    }

    @Operation(
            operationId = "generarApiKey",
            summary = "Generar o rotar una API Key",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API Key generada correctamente."),
        @ApiResponse(responseCode = "400", description = "Solicitud no vÃ¡lida.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o invÃ¡lido.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Otra solicitud estÃ¡ generando una API Key.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Fallo interno.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "503", description = "Base de datos no disponible.",
                content = @Content(schema = @Schema(implementation = com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO.class)))
    })
    @PostMapping("/apikey")
    public ResponseEntity<SuccessResponseDTO<ApiKeyResponseDTO>> generarApiKey(
            @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @RequestBody(required = false) String cuerpo) {
        if (cuerpo != null && !cuerpo.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "La solicitud no es válida.");
        }
        if (jwt == null || jwt.getSubject() == null || !jwt.getSubject().startsWith("usr_")) {
            throw new org.springframework.security.authentication.BadCredentialsException("No autorizado.");
        }
        UUID usuario = UUID.fromString(jwt.getSubject().substring(4));
        ApiKeyResponseDTO respuesta = mapear(servicio.generarApiKey(usuario));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new SuccessResponseDTO<>("API Key generada correctamente. Guardá esta clave; no se volverá a mostrar.", respuesta));
    }

    private static UsuarioResponseDTO mapear(UsuarioResultado resultado) {
        return new UsuarioResponseDTO("usr_" + resultado.id(), resultado.nombreUsuario(),
                resultado.correo(), resultado.creadoEn());
    }

    private static LoginResponseDTO mapear(LoginResultado resultado) {
        return new LoginResponseDTO(resultado.tokenAcceso());
    }

    private static ApiKeyResponseDTO mapear(ApiKeyResultado resultado) {
        return new ApiKeyResponseDTO(resultado.apiKey(), resultado.prefijo(), resultado.activa(), resultado.creadaEn());
    }
}
