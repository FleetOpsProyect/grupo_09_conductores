package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.event.DriverUpdatedPayload;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.FieldNotEditableException;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateDriverServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T21:00:00Z"), ZoneId.of("America/Bogota"));
    private static final String DOCUMENT = "1144056789";

    @Mock
    private DriverRepository repository;

    @Test
    void updateChangesDriverAndSavesIt() {
        Driver driver = driverWithPhone(null);

        Driver result = service()
                .update(driver.getId(), new UpdateDriver.Command("Carlos A. Pérez", "nuevo@correo.com", "3001234567"));

        assertThat(result.getFullName()).isEqualTo("Carlos A. Pérez");
        assertThat(result.getEmail()).isEqualTo("nuevo@correo.com");
        verify(repository).updateWithOutbox(same(driver), any(OutboxEvent.class));
    }

    @Test
    void updateKeepsTheStatus() {
        Driver driver = driverWithPhone(null);
        DriverStatus before = driver.getStatus();

        Driver result = service()
                .update(driver.getId(), new UpdateDriver.Command("Carlos A. Pérez", "nuevo@correo.com", null));

        assertThat(result.getStatus()).isEqualTo(before);
    }

    @Test
    void missingPhoneKeepsTheStoredPhone() {
        Driver driver = driverWithPhone("3001234567");

        Driver result = service()
                .update(driver.getId(), UpdateDriver.Command.keepingPhone("Carlos A. Pérez", "nuevo@correo.com"));

        assertThat(result.getPhone()).isEqualTo("3001234567");
        assertThat(result.getFullName()).isEqualTo("Carlos A. Pérez");
        verify(repository).updateWithOutbox(same(driver), any(OutboxEvent.class));
    }

    @Test
    void nullPhoneErasesTheStoredPhone() {
        Driver driver = driverWithPhone("3001234567");

        Driver result = service()
                .update(driver.getId(), new UpdateDriver.Command("Carlos Pérez", "carlos@correo.com", null));

        assertThat(result.getPhone()).isNull();
    }

    @Test
    void phoneValueReplacesTheStoredPhone() {
        Driver driver = driverWithPhone("3001234567");

        Driver result = service()
                .update(driver.getId(), new UpdateDriver.Command("Carlos Pérez", "carlos@correo.com", "3159876543"));

        assertThat(result.getPhone()).isEqualTo("3159876543");
    }

    @Test
    void differentDocumentNumberIsRejectedAndNothingIsSaved() {
        Driver driver = driverWithPhone(null);
        UpdateDriver.Command command = new UpdateDriver.Command(
                "Carlos A. Pérez", "carlos@correo.com", false, null, "999999", null);

        assertThatThrownBy(() -> service().update(driver.getId(), command))
                .isInstanceOfSatisfying(FieldNotEditableException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("document_number"));
        assertThat(driver.getFullName()).isEqualTo("Carlos Pérez");
        verify(repository).findById(driver.getId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void sameDocumentNumberIsAccepted() {
        Driver driver = driverWithPhone(null);
        UpdateDriver.Command command = new UpdateDriver.Command(
                "Carlos A. Pérez", "carlos@correo.com", false, null, " " + DOCUMENT + " ", null);

        Driver result = service().update(driver.getId(), command);

        assertThat(result.getFullName()).isEqualTo("Carlos A. Pérez");
        assertThat(result.getDocumentNumber()).isEqualTo(DOCUMENT);
    }

    @Test
    void changeSavesDriverUpdatedEventWithTheNewNameAndTheCorrelationId() {
        Driver driver = driverWithPhone("3001234567");
        UUID correlationId = UUID.randomUUID();
        UpdateDriver.Command command = new UpdateDriver.Command(
                "Carlos A. Pérez", "carlos@correo.com", false, null, null, correlationId);

        service().update(driver.getId(), command);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).updateWithOutbox(same(driver), captor.capture());
        EventEnvelope<?> envelope = captor.getValue().envelope();
        assertThat(envelope.eventType()).isEqualTo("driver_updated_event");
        assertThat(envelope.source()).isEqualTo("driver");
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.occurredAt()).isEqualTo(OffsetDateTime.now(CLOCK));
        assertThat(envelope.data()).isEqualTo(new DriverUpdatedPayload(driver.getId(), "Carlos A. Pérez"));
        assertThat(captor.getValue().status()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(driver.getUpdatedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
    }

    @Test
    void missingCorrelationIdGetsANewOne() {
        Driver driver = driverWithPhone(null);

        service().update(driver.getId(), new UpdateDriver.Command("Carlos Pérez", "nuevo@correo.com", null));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).updateWithOutbox(same(driver), captor.capture());
        assertThat(captor.getValue().envelope().correlationId()).isNotNull();
    }

    @Test
    void sameValuesSaveNothingAndCreateNoEvent() {
        Driver driver = driverWithPhone("3001234567");
        OffsetDateTime updatedAt = driver.getUpdatedAt();

        Driver result = service()
                .update(driver.getId(), new UpdateDriver.Command("Carlos Pérez", "carlos@correo.com", "3001234567"));

        assertThat(result).isSameAs(driver);
        assertThat(result.getUpdatedAt()).isEqualTo(updatedAt);
        verify(repository).findById(driver.getId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingPhoneIsNotAChange() {
        Driver driver = driverWithPhone("3001234567");

        service().update(driver.getId(), UpdateDriver.Command.keepingPhone("Carlos Pérez", "carlos@correo.com"));

        verify(repository).findById(driver.getId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void updateThrowsWhenDriverDoesNotExistAndSavesNothing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service()
                .update(id, new UpdateDriver.Command("Carlos Pérez", "carlos@correo.com", null)))
                .isInstanceOf(DriverNotFoundException.class);
        verify(repository, never()).updateWithOutbox(any(), any());
        verify(repository, never()).save(any(Driver.class));
    }

    @Test
    void updatePropagatesPersistenceUnavailableInsteadOfNotFoundAndSavesNothing() {
        UUID id = UUID.randomUUID();
        PersistenceUnavailableException unavailable = new PersistenceUnavailableException("down", null);
        when(repository.findById(id)).thenThrow(unavailable);

        assertThatThrownBy(() -> service()
                .update(id, new UpdateDriver.Command("Carlos Pérez", "carlos@correo.com", null)))
                .isSameAs(unavailable);
        verify(repository, never()).updateWithOutbox(any(), any());
    }

    private UpdateDriverService service() {
        return new UpdateDriverService(repository, CLOCK);
    }

    private Driver driverWithPhone(String phone) {
        Driver driver = Driver.register("Carlos Pérez", DOCUMENT, "carlos@correo.com", phone,
                OffsetDateTime.now(CLOCK).minusDays(1));
        when(repository.findById(driver.getId())).thenReturn(Optional.of(driver));
        return driver;
    }
}
