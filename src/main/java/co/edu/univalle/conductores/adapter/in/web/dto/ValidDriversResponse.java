package co.edu.univalle.conductores.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Respuesta del listado de conductores con licencia vigente a una fecha (HU-07).
 *
 * @param content   conductores vigentes encontrados, ordenados por {@code created_at} (máximo 500)
 * @param total     cantidad total de conductores vigentes encontrados (aunque {@code content} se
 *                  trunque en 500)
 * @param truncated indica si existen más de 500 conductores vigentes y {@code content} fue truncado
 */
@Schema(description = "Listado no paginado de conductores con licencia vigente a una fecha (máximo 500 elementos)")
public record ValidDriversResponse(
        @Schema(description = "Conductores con licencia vigente ordenados por created_at (máximo 500)")
        List<DriverResponse> content,
        @Schema(description = "Total de conductores con licencia vigente encontrados", example = "12")
        long total,
        @Schema(description = "Indica si el listado superó el límite de 500 conductores y fue truncado",
                example = "false")
        boolean truncated) {
}
