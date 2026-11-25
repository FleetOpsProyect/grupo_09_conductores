package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.DriverUpdatedPayload;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.FieldNotEditableException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: actualizar los datos editables de un conductor (HU-08).
 *
 * <p>Si al menos un campo cambia, guarda el conductor y el evento driver_updated_event en una sola
 * transacción. Si nada cambia, no guarda ni genera evento. No llama a Documentación.
 */
public class UpdateDriverService implements UpdateDriver {

    private static final String DOCUMENT_NUMBER = "document_number";

    private final DriverRepository repository;
    private final Clock clock;

    /**
     * Crea el servicio con sus dependencias.
     *
     * @param repository puerto de persistencia de conductores
     * @param clock      reloj para la fecha de modificación
     */
    public UpdateDriverService(DriverRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Driver update(UUID id, Command command) {
        Driver driver = repository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
        rejectDocumentNumberChange(driver, command.documentNumber());

        String phone = command.phonePresent() ? command.phone() : driver.getPhone();
        if (!changes(driver, command, phone)) {
            return driver;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        driver.update(command.fullName(), command.email(), phone, now);
        UUID correlationId = command.correlationId() != null ? command.correlationId() : UUID.randomUUID();
        EventEnvelope<DriverUpdatedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                DriverEventType.DRIVER_UPDATED.value(),
                now,
                "driver",
                correlationId,
                new DriverUpdatedPayload(driver.getId(), driver.getFullName()));
        repository.updateWithOutbox(driver, OutboxEvent.pending(envelope));
        return driver;
    }

    private static void rejectDocumentNumberChange(Driver driver, String documentNumber) {
        if (driver.isDifferentDocumentNumber(documentNumber)) {
            throw new FieldNotEditableException(DOCUMENT_NUMBER);
        }
    }

    /**
     * The web layer already trims full_name and rejects an email with spaces, so the values are compared as
     * they arrive.
     */
    private static boolean changes(Driver driver, Command command, String phone) {
        return !Objects.equals(command.fullName(), driver.getFullName())
                || !Objects.equals(command.email(), driver.getEmail())
                || !Objects.equals(phone, driver.getPhone());
    }
}
