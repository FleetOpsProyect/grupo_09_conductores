package co.edu.univalle.conductores.application.port.in;

import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * Puerto de entrada: listar conductores cuya licencia está vigente a una fecha determinada (HU-07).
 */
public interface ListValidDrivers {

    /**
     * Lista conductores elegibles cuya licencia está vigente en {@code validUntil}.
     *
     * @param query filtro de estado y fecha límite de vigencia
     * @return resultado con máximo 500 conductores ordenados por {@code created_at}
     */
    ValidDriversResult listValid(Query query);

    /**
     * Filtro de búsqueda por estado y fecha de vigencia.
     *
     * @param status     estado a filtrar, o {@code null} para no filtrar por estado
     * @param validUntil fecha límite de vigencia de la licencia
     */
    record Query(DriverStatus status, LocalDate validUntil) {
    }

    /**
     * Resultado del listado de conductores vigentes.
     *
     * @param content   conductores vigentes ordenados por {@code created_at} (máximo 500)
     * @param total     cantidad total de conductores vigentes encontrados, aunque {@code content} se
     *                  trunque en 500
     * @param truncated {@code true} si existen más de 500 conductores vigentes
     */
    record ValidDriversResult(List<Driver> content, long total, boolean truncated) {
    }
}
