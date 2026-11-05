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
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Manejador global de errores: convierte las excepciones en el formato de error común.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_DATA = "INVALID_DATA";

    private final JsonFieldNameResolver fieldNames;

    /**
     * Creates the handler.
     *
     * @param fieldNames resolves the JSON name of invalid fields
     */
    public GlobalExceptionHandler(JsonFieldNameResolver fieldNames) {
        this.fieldNames = fieldNames;
    }

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
     * Invalid request body: 400 INVALID_DATA with one entry per invalid field.
     *
     * @param ex thrown exception
     * @param request current request
     * @return common error with the per-field detail
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Object target = ex.getBindingResult().getTarget();
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(fieldNames.resolve(target, error.getField()),
                        ValidationErrorCodes.fromConstraint(error.getCode())))
                .toList();
        return build(HttpStatus.BAD_REQUEST, INVALID_DATA, "Hay datos inválidos en la solicitud",
                request, ValidationErrorCodes.onePerField(errors));
    }

    /**
     * Invalid request parameters such as page or size: 400 INVALID_DATA with one entry per parameter.
     *
     * @param ex thrown exception
     * @param request current request
     * @return common error with the per-parameter detail
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParams(HandlerMethodValidationException ex,
            HttpServletRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(result.getMethodParameter().getParameterName(),
                                ValidationErrorCodes.fromMessageCodes(error.getCodes()))))
                .toList();
        return build(HttpStatus.BAD_REQUEST, INVALID_DATA, "Hay parámetros inválidos", request,
                ValidationErrorCodes.onePerField(errors));
    }

    /**
     * Parameter that cannot be converted to its type, such as page=abc or an unknown status: 400 INVALID_DATA.
     *
     * @param ex thrown exception
     * @param request current request
     * @return common error with the invalid parameter
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        FieldError error = new FieldError(ex.getName(), ValidationErrorCodes.fromTargetType(ex.getRequiredType()));
        return build(HttpStatus.BAD_REQUEST, INVALID_DATA, "Hay parámetros inválidos", request, List.of(error));
    }

    /**
     * Unreadable body. A value with the wrong type for a field is 400 INVALID_DATA for that field;
     * a body that is not valid JSON is 400 INVALID_JSON.
     *
     * @param ex thrown exception
     * @param request current request
     * @return common error
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            FieldError error = new FieldError(fieldNames.fromJacksonPath(mismatch.getPath()),
                    ValidationErrorCodes.fromTargetType(mismatch.getTargetType()));
            return build(HttpStatus.BAD_REQUEST, INVALID_DATA, "Hay datos inválidos en la solicitud",
                    request, List.of(error));
        }
        return build(HttpStatus.BAD_REQUEST, "INVALID_JSON", "El cuerpo de la solicitud no es un JSON válido",
                request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request, List<FieldError> errors) {
        ErrorResponse body = new ErrorResponse(code, message,
                OffsetDateTime.now(ZoneId.of("America/Bogota")), request.getRequestURI(), errors);
        return ResponseEntity.status(status).body(body);
    }
}
