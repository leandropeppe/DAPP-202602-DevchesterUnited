package com.devchesterunited.devchesterunitedv2.exception;

import com.devchesterunited.devchesterunitedv2.controller.dto.ErrorResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2PQuoteResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2PErrorResponseDTO;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ExcepcionSimulacion;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(JugadorNoEncontradoException.class)
    public ResponseEntity<Object> playerNotFound(JugadorNoEncontradoException exception) {
        return response(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CotizacionNoEncontradaException.class)
    public ResponseEntity<Object> valuationNotFound(CotizacionNoEncontradaException exception) {
        return response(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConsultaValorizacionInvalidaException.class)
    public ResponseEntity<Object> invalidValuationQuery(ConsultaValorizacionInvalidaException exception) {
        return response(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SincronizacionEnCursoException.class)
    public ResponseEntity<Object> syncConflict(SincronizacionEnCursoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO("Ya hay una sincronizacion del catalogo en curso"));
    }

    @ExceptionHandler(SincronizacionPersistenciaException.class)
    public ResponseEntity<Object> syncPersistenceUnavailable(SincronizacionPersistenciaException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(FootballDataException.class)
    public ResponseEntity<Object> providerUnavailable(FootballDataException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponseDTO("No fue posible sincronizar el catalogo con el proveedor externo"));
    }

    @ExceptionHandler(TokenMarketException.class)
    public ResponseEntity<Object> tokenMarket(TokenMarketException exception,
            HttpServletRequest request) {
        HttpStatus status = statusFor(exception.reason());
        if (exception.reason() == TokenMarketException.Reason.QUOTE_UNAVAILABLE
                && isP2PPurchaseRequest(request)) {
            return ResponseEntity.status(status).body(new MarketP2PErrorResponseDTO(
                    genericMessage(status), publicCode(exception.reason()), null, "SUSPENDED"));
        }
        return ResponseEntity.status(status).body(new ErrorResponseDTO(
                genericMessage(status), publicCode(exception.reason()),
                MarketP2PQuoteResponseDTO.fromDomain(exception.currentQuote())));
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> invalidInput(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler(ExcepcionSimulacion.class)
    public ResponseEntity<Object> simulationError(ExcepcionSimulacion exception) {
        HttpStatus status = switch (exception.codigo()) {
            case HORIZONTE_INVALIDO, METRICA_NO_EDITABLE, VALOR_FUERA_DE_RANGO -> HttpStatus.BAD_REQUEST;
            case DATOS_INSUFICIENTES, POSICION_NO_SOPORTADA, POSICION_AMBIGUA,
                    ESTRATEGIA_AUSENTE -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ErrorResponseDTO(exception.getMessage()));
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

    private static HttpStatus statusFor(TokenMarketException.Reason reason) {
        return switch (reason) {
            case MARKET_NOT_READY -> HttpStatus.SERVICE_UNAVAILABLE;
            case ACCOUNT_NOT_FOUND, OPERATION_NOT_FOUND, OFFER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_INPUT, MANUAL_PRICE_NOT_ALLOWED -> HttpStatus.BAD_REQUEST;
            case INACTIVE_PLAYER, QUOTE_UNAVAILABLE, INSUFFICIENT_BALANCE,
                    INSUFFICIENT_INVENTORY,
                    IDEMPOTENCY_CONFLICT, STALE_REQUEST, QUOTE_CHANGED,
                    OFFER_ALREADY_CANCELLED, OFFER_NOT_PURCHASABLE,
                    INSUFFICIENT_AVAILABLE_QUANTITY, INSUFFICIENT_CREDITS,
                    HISTORICAL_RESULT_UNAVAILABLE -> HttpStatus.CONFLICT;
        };
    }

    private static boolean isP2PPurchaseRequest(HttpServletRequest request) {
        return request != null
                && request.getRequestURI().matches("/api/v1/market/p2p/offers/[^/]+/purchases");
    }

    private static String publicCode(TokenMarketException.Reason reason) {
        return reason == TokenMarketException.Reason.INSUFFICIENT_BALANCE
                ? "INSUFFICIENT_CREDITS" : reason.name();
    }

    private static String genericMessage(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "La solicitud no es válida.";
            case 404 -> "Recurso no encontrado.";
            case 409 -> "La solicitud entra en conflicto con el estado actual del recurso.";
            case 503 -> "El servicio no está disponible temporalmente.";
            default -> "No se pudo procesar la solicitud.";
        };
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
