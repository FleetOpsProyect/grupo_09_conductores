package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Caso de uso: actualizar los datos editables de un conductor.
 */
public class UpdateDriverService implements UpdateDriver {

    private final DriverRepository repository;
    private final Clock clock;

    /**
     * Crea el servicio con sus dependencias.
     *
     * @param repository puerto de persistencia de conductores
     * @param clock reloj para la fecha de modificación
     */
    public UpdateDriverService(DriverRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Driver update(UUID id, Command command) {
        Driver driver = repository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
        driver.update(command.fullName(), command.email(), command.phone(), OffsetDateTime.now(clock));
        repository.save(driver);
        return driver;
    }
}
