package co.edu.univalle.conductores.domain;

import java.util.List;

/**
 * Una página de resultados, con el total para que el cliente pueda paginar.
 *
 * @param content elementos de la página
 * @param page número de página desde 0
 * @param size tamaño de página
 * @param total cantidad total de elementos
 * @param <T> tipo de elemento
 */
public record PageResult<T>(List<T> content, int page, int size, long total) {
}
