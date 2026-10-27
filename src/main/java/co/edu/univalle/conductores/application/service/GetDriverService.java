package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import java.util.UUID;

/**
 * Caso de uso: consultar un conductor por id.
 */
public class GetDriverService implements GetDriver {

    private final DriverRepository repository;

    /**
     * Crea el servicio con su repositorio.
     *
     * @param repository puerto de persistencia de conductores
     */
    public GetDriverService(DriverRepository repository) {
        this.repository = repository;
    }

    @Override
    public Driver get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
    }
}
