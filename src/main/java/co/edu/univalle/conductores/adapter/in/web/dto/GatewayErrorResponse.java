package co.edu.univalle.conductores.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of the 401 and 403 responses that API Gateway returns when its authorizer rejects a request.
 *
 * <p>Documentation only: the service never builds this object. API Gateway answers before the request
 * reaches the service, so these responses do not follow the common error format.
 *
 * @param message "Unauthorized" or "Forbidden"
 */
@Schema(description = """
        Respuesta de API Gateway cuando su authorizer rechaza la petición. No la genera el servicio y no \
        sigue el formato común de error.""")
public record GatewayErrorResponse(
        @Schema(description = "\"Unauthorized\" (401) o \"Forbidden\" (403)", example = "Forbidden")
        String message) {
}
