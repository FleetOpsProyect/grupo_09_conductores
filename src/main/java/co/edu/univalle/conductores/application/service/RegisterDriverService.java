package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Caso de uso: registrar un conductor nuevo.
 */
public class RegisterDriverService implements RegisterDriver {

    private final DriverRepository repository;
    private final Clock clock;

    /**
     * Crea el servicio con sus dependencias.
     *
     * @param repository puerto de persistencia de conductores
     * @param clock reloj para las fechas de auditoría
     */
    public RegisterDriverService(DriverRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Driver register(Command command) {
        Driver driver = Driver.register(command.fullName(), command.documentNumber(),
                command.email(), command.phone(), OffsetDateTime.now(clock));
        repository.createWithUniqueness(driver);
        return driver;
    }
}
