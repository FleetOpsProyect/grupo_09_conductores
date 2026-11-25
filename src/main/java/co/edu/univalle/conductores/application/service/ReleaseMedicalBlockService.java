package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.ReleaseMedicalBlock;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
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

    /**
     * Quita el bloqueo médico sobre el conductor que lee la transacción del repositorio, no sobre una
     * lectura previa, para no pisar un bloqueo por documentos aplicado entre tanto. Si queda ACTIVE,
     * devuelve driver_activated_event para el outbox.
     */
    @Override
    public Driver release(UUID id, Command command) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID correlationId = command.correlationId() != null ? command.correlationId() : UUID.randomUUID();

        return repository.releaseMedical(id, command.notes(), now, driver -> {
            driver.releaseMedicalBlock(now);
            if (driver.getStatus() != DriverStatus.ACTIVE) {
                return null;
            }
            EventEnvelope<DriverActivatedPayload> envelope = new EventEnvelope<>(
                    UUID.randomUUID(),
                    DriverEventType.DRIVER_ACTIVATED.value(),
                    now,
                    "driver",
                    correlationId,
                    new DriverActivatedPayload(driver.getId(), driver.getFullName()));
            return OutboxEvent.pending(envelope);
        });
    }
}
