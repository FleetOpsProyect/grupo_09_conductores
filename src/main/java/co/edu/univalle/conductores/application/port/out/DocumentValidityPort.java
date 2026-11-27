package co.edu.univalle.conductores.application.port.out;

import co.edu.univalle.conductores.domain.DocumentationUnavailableException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de salida: consulta al servicio externo de Documentación para validar la vigencia de
 * licencias de conducir a una fecha determinada (HU-07).
 */
public interface DocumentValidityPort {

    /** Cantidad máxima de identificadores de conductor admitidos por cada consulta en lote. */
    int MAX_BATCH_SIZE = 100;

    /**
     * Consulta qué conductores del lote tienen licencia de conducción vigente en la fecha indicada.
     *
     * <p>Cada llamada recibe un lote de hasta {@value #MAX_BATCH_SIZE} identificadores de conductor y
     * la fecha {@code validUntil}. Devuelve únicamente los identificadores cuya licencia está vigente
     * hasta esa fecha (los conductores sin licencia registrada o con licencia vencida no se incluyen).
     *
     * @param driverIds  lista de hasta 100 identificadores de conductor a consultar
     * @param validUntil fecha límite de vigencia de la licencia
     * @return conjunto de identificadores cuya licencia está vigente en {@code validUntil}
     * @throws DocumentationUnavailableException si el servicio de Documentación excede el timeout,
     *         responde con error 5xx o el circuit breaker se encuentra abierto
     */
    Set<UUID> findDriversWithValidLicense(List<UUID> driverIds, LocalDate validUntil);
}
