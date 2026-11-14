package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

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
        OffsetDateTime now = OffsetDateTime.now(clock);
        Driver driver = Driver.register(command.fullName(), command.documentNumber(),
                command.email(), command.phone(), now);

        EventEnvelope<DriverActivatedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                DriverEventType.DRIVER_ACTIVATED.value(),
                now,
                "driver",
                command.correlationId(),
                new DriverActivatedPayload(driver.getId(), driver.getFullName()));

        repository.createWithUniquenessAndOutbox(driver, OutboxEvent.pending(envelope));
        return driver;
    }
}
