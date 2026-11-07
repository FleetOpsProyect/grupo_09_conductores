package co.edu.univalle.conductores.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato de error común de la API.
 *
 * @param code código de error con nombre explícito
 * @param message descripción del error
 * @param timestamp momento del error en GMT-5
 * @param path ruta de la petición
 * @param errors detalle por campo, solo en errores de validación
 */
public record ErrorResponse(String code, String message, OffsetDateTime timestamp, String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> errors) {

    /**
     * Error of a single field or parameter.
     *
     * @param field JSON name of the field or parameter
     * @param code validation code, for example REQUIRED or INVALID_FORMAT
     */
    public record FieldError(String field, String code) {
    }
}
