package co.edu.univalle.conductores.adapter.in.web;

import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse.FieldError;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.InvalidDriverException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejador global de errores: convierte las excepciones en el formato de error común.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Conductor inexistente (404).
     *
     * @param ex excepción lanzada
     * @param request petición actual
     * @return error común
     */
    @ExceptionHandler(DriverNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DriverNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", ex.getMessage(), request, List.of());
    }

    /**
     * Documento duplicado (409).
     *
     * @param ex excepción lanzada
     * @param request petición actual
     * @return error común
     */
    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateDocumentException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_DRIVER", ex.getMessage(), request, List.of());
    }

    /**
     * Datos que no cumplen las reglas del dominio (400).
     *
     * @param ex excepción lanzada
     * @param request petición actual
     * @return error común
     */
    @ExceptionHandler(InvalidDriverException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDriver(InvalidDriverException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_DRIVER", ex.getMessage(), request, List.of());
    }

    /**
     * Errores de validación del cuerpo, con todos los campos inválidos (400).
     *
     * @param ex excepción lanzada
     * @param request petición actual
     * @return error común con el detalle por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay datos inválidos en la solicitud",
                request, errors);
    }

    /**
     * Errores de validación de parámetros de consulta, como page o size (400).
     *
     * @param ex excepción lanzada
     * @param request petición actual
     * @return error común con el detalle por parámetro
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParams(HandlerMethodValidationException ex,
            HttpServletRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay parámetros inválidos", request, errors);
    }

    /**
     * Valores con formato incorrecto, como un id que no es UUID o un estado desconocido (400).
     *
     * @param ex excepción lanzada
     * @param request petición actual
     * @return error común
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Formato de datos inválido", request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request, List<FieldError> errors) {
        ErrorResponse body = new ErrorResponse(code, message,
                OffsetDateTime.now(ZoneId.of("America/Bogota")), request.getRequestURI(), errors);
        return ResponseEntity.status(status).body(body);
    }
}
