package co.edu.univalle.conductores.adapter.in.web;

import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse.FieldError;
import java.util.List;

/**
 * Excepción para errores de validación en parámetros de consulta (query params) que se
 * validan condicionalmente en el controlador, como {@code page}/{@code size} en HU-06 o
 * {@code valid_until} en HU-07.
 */
class InvalidQueryParamsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<FieldError> errors;

    InvalidQueryParamsException(List<FieldError> errors) {
        super("Hay parámetros inválidos");
        this.errors = List.copyOf(errors);
    }

    List<FieldError> getErrors() {
        return errors;
    }
}
