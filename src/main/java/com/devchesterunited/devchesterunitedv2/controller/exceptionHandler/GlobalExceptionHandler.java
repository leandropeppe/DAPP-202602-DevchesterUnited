package com.devchesterunited.devchesterunitedv2.controller.exceptionHandler;

import com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> invalidInput(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> unauthorized(AuthenticationException exception) {
        String mensaje = exception instanceof BadCredentialsException
                && "Nombre de usuario o contraseña incorrectos".equals(exception.getMessage())
                ? exception.getMessage() : "No autorizado.";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDTO(mensaje));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<Object> conflict(DataAccessException exception) {
        String mensaje = exception.getMessage() != null && exception.getMessage().contains("ya está registrado")
                ? "El nombre de usuario o el correo electrónico ya está registrado."
                : "La solicitud entra en conflicto con el estado actual del recurso.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO(mensaje));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (!request.getDescription(false).contains("/api/v1/auth/")) {
            return response(HttpStatus.BAD_REQUEST);
        }
        var detalles = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponseDTO.DetalleErrorDTO(nombreJson(error.getField()), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponseDTO("Error de validación", detalles));
    }

    private static String nombreJson(String campo) {
        return switch (campo) {
            case "nombreUsuario" -> "username";
            case "correo" -> "email";
            case "contrasena" -> "password";
            default -> campo;
        };
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> unavailable(DataAccessException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Reemplaza tambien ProblemDetail de Spring: nunca exponer detail ni mensajes internos. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return super.handleExceptionInternal(exception, error(status), headers, status, request);
    }

    private static ResponseEntity<Object> response(HttpStatus status) {
        return ResponseEntity.status(status).body(error(status));
    }

    private static ErrorResponseDTO error(HttpStatusCode status) {
        String message = switch (status.value()) {
            case 400 -> "La solicitud no es válida.";
            case 401 -> "No autorizado.";
            case 403 -> "Acceso denegado.";
            case 404 -> "Recurso no encontrado.";
            case 405 -> "Método no permitido.";
            case 409 -> "La solicitud entra en conflicto con el estado actual del recurso.";
            case 415 -> "Tipo de contenido no admitido.";
            case 503 -> "El servicio no está disponible temporalmente.";
            default -> status.is5xxServerError() ? "Ocurrió un error interno." : "No se pudo procesar la solicitud.";
        };
        return new ErrorResponseDTO(message);
    }
}
