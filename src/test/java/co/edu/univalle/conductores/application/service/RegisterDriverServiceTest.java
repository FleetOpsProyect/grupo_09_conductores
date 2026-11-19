package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterDriverServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-22T21:00:00Z"), ZoneId.of("America/Bogota"));
    private static final UUID CORRELATION_ID =
            UUID.fromString("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

    @Mock
    private DriverRepository repository;

    private RegisterDriverService service;

    @BeforeEach
    void setUp() {
        service = new RegisterDriverService(repository, CLOCK);
    }

    @Test
    void registerSavesActiveDriverAndPendingActivationEvent() {
        RegisterDriver.Command command = command();

        Driver result = service.register(command);

        ArgumentCaptor<Driver> driverCaptor = ArgumentCaptor.forClass(Driver.class);
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).createWithUniquenessAndOutbox(driverCaptor.capture(), eventCaptor.capture());

        OutboxEvent outboxEvent = eventCaptor.getValue();
        assertThat(driverCaptor.getValue()).isSameAs(result);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(result.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-10-22T16:00:00-05:00"));
        assertThat(outboxEvent.status()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(outboxEvent.attempts()).isZero();
        assertThat(outboxEvent.envelope().eventId()).isNotNull();
        assertThat(outboxEvent.envelope().eventType()).isEqualTo(DriverEventType.DRIVER_ACTIVATED.value());
        assertThat(outboxEvent.envelope().occurredAt()).isEqualTo(result.getCreatedAt());
        assertThat(outboxEvent.envelope().source()).isEqualTo("driver");
        assertThat(outboxEvent.envelope().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(outboxEvent.envelope().data())
                .isEqualTo(new DriverActivatedPayload(result.getId(), result.getFullName()));
    }

    @Test
    void registerPropagatesDuplicateDocument() {
        doThrow(new DuplicateDocumentException("1144056789"))
                .when(repository)
                .createWithUniquenessAndOutbox(any(Driver.class), any(OutboxEvent.class));

        assertThatThrownBy(() -> service.register(command()))
                .isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void registerPropagatesPersistenceUnavailable() {
        PersistenceUnavailableException unavailable = new PersistenceUnavailableException("down", null);
        doThrow(unavailable)
                .when(repository)
                .createWithUniquenessAndOutbox(any(Driver.class), any(OutboxEvent.class));

        assertThatThrownBy(() -> service.register(command())).isSameAs(unavailable);
    }

    private static RegisterDriver.Command command() {
        return new RegisterDriver.Command(
                "Carlos Pérez", "1144056789", "carlos@correo.com", null, CORRELATION_ID);
    }
}
