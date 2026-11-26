package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.ListValidDrivers;
import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso: listar conductores con licencia vigente a una fecha (HU-07).
 *
 * <p>Obtiene los candidatos elegibles (excluyendo {@link DriverStatus#MEDICAL_HOLD_INCIDENT}), los
 * consulta en Documentación en lotes de hasta {@value DocumentValidityPort#MAX_BATCH_SIZE} ids y
 * devuelve los conductores vigentes ordenados ascendentemente por {@code created_at}, truncando
 * {@code content} a {@value #MAX_RESULTS} elementos.
 */
public class ListValidDriversService implements ListValidDrivers {

    static final int MAX_RESULTS = 500;

    private static final Logger log = LoggerFactory.getLogger(ListValidDriversService.class);

    private final DriverRepository repository;
    private final DocumentValidityPort documentValidityPort;

    /**
     * Crea el servicio con sus puertos de salida.
     *
     * @param repository           puerto de persistencia de conductores
     * @param documentValidityPort puerto de consulta de vigencia de licencias
     */
    public ListValidDriversService(DriverRepository repository, DocumentValidityPort documentValidityPort) {
        this.repository = repository;
        this.documentValidityPort = documentValidityPort;
    }

    @Override
    public ValidDriversResult listValid(Query query) {
        if (query.status() == DriverStatus.MEDICAL_HOLD_INCIDENT) {
            return new ValidDriversResult(List.of(), 0L, false);
        }

        List<Driver> candidates = repository.findCandidates(query.status()).stream()
                .filter(driver -> driver.getStatus() != DriverStatus.MEDICAL_HOLD_INCIDENT)
                .toList();
        if (candidates.isEmpty()) {
            log.info("Listado de conductores vigentes hasta {} sin candidatos elegibles", query.validUntil());
            return new ValidDriversResult(List.of(), 0L, false);
        }

        List<UUID> candidateIds = candidates.stream().map(Driver::getId).toList();
        Set<UUID> validIds = new HashSet<>();
        for (int start = 0; start < candidateIds.size(); start += DocumentValidityPort.MAX_BATCH_SIZE) {
            int end = Math.min(start + DocumentValidityPort.MAX_BATCH_SIZE, candidateIds.size());
            List<UUID> batch = candidateIds.subList(start, end);
            validIds.addAll(documentValidityPort.findDriversWithValidLicense(batch, query.validUntil()));
        }

        // Los ids devueltos por Documentación que no estén en candidates se ignoran al filtrar sobre candidates.
        List<Driver> validDrivers = candidates.stream()
                .filter(driver -> validIds.contains(driver.getId()))
                .sorted(Comparator.comparing(Driver::getCreatedAt))
                .toList();

        long total = validDrivers.size();
        boolean truncated = total > MAX_RESULTS;
        List<Driver> content = truncated ? validDrivers.subList(0, MAX_RESULTS) : validDrivers;

        log.info("Listado de conductores vigentes hasta {} completado: candidatos={}, vigentes={}, truncado={}",
                query.validUntil(), candidates.size(), total, truncated);
        return new ValidDriversResult(content, total, truncated);
    }
}
