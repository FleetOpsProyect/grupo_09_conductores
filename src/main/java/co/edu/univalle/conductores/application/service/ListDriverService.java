package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.PageResult;

/**
 * Caso de uso: listar conductores con filtro y paginación.
 */
public class ListDriverService implements ListDriver {

    private final DriverRepository repository;

    /**
     * Crea el servicio con su repositorio.
     *
     * @param repository puerto de persistencia de conductores
     */
    public ListDriverService(DriverRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<Driver> list(Query query) {
        return repository.findAll(query.status(), query.page(), query.size());
    }
}
