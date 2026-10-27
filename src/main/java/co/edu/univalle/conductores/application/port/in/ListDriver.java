package co.edu.univalle.conductores.application.port.in;

import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PageResult;

/**
 * Puerto de entrada: listar conductores con filtro y paginación.
 */
public interface ListDriver {

    /**
     * Lista conductores.
     *
     * @param query filtro y paginación
     * @return una página de conductores
     */
    PageResult<Driver> list(Query query);

    /**
     * Filtro y paginación del listado.
     *
     * @param status estado a filtrar, o null para no filtrar
     * @param page número de página desde 0
     * @param size tamaño de página
     */
    record Query(DriverStatus status, int page, int size) {
    }
}
