package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.ReleaseMedicalBlock;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Caso de uso: registrar alta médica de un conductor para habilitar su estado operativo.
 */
public class ReleaseMedicalBlockService implements ReleaseMedicalBlock {

    private final DriverRepository repository;
    private final Clock clock;

    /**
     * Crea el servicio con sus dependencias.
     *
     * @param repository puerto de persistencia de conductores
     * @param clock reloj para las marcas de tiempo
     */
    public ReleaseMedicalBlockService(DriverRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Driver release(UUID id, Command command) {
        Driver driver = repository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
        OffsetDateTime now = OffsetDateTime.now(clock);
        driver.releaseMedicalBlock(now);

        OutboxEvent outboxEvent = null;
        if (driver.getStatus() == DriverStatus.ACTIVE) {
            UUID correlationId = command.correlationId() != null ? command.correlationId() : UUID.randomUUID();
            EventEnvelope<DriverActivatedPayload> envelope = new EventEnvelope<>(
                    UUID.randomUUID(),
                    DriverEventType.DRIVER_ACTIVATED.value(),
                    now,
                    "driver",
                    correlationId,
                    new DriverActivatedPayload(driver.getId(), driver.getFullName()));
            outboxEvent = OutboxEvent.pending(envelope);
        }

        repository.saveMedicalRelease(driver, command.notes(), now, outboxEvent);
        return driver;
    }
}
