package co.edu.univalle.conductores.adapter.in.web.dto;

import java.util.List;

/**
 * Página de conductores en la API.
 *
 * @param content conductores de la página
 * @param page número de página desde 0
 * @param size tamaño de página
 * @param total cantidad total de conductores que cumplen el filtro
 */
public record DriverPageResponse(List<DriverResponse> content, int page, int size, long total) {
}
