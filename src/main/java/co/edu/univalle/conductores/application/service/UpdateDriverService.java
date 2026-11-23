package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.FieldNotEditableException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Caso de uso: actualizar los datos editables de un conductor.
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
        driver.update(command.fullName(), command.email(), phone, OffsetDateTime.now(clock));
        repository.save(driver);
        return driver;
    }

    private static void rejectDocumentNumberChange(Driver driver, String documentNumber) {
        if (documentNumber != null && !documentNumber.trim().equals(driver.getDocumentNumber())) {
            throw new FieldNotEditableException(DOCUMENT_NUMBER);
        }
    }
}
