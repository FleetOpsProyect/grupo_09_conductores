package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.ReleaseMedicalBlock;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.StatusNotAllowedException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The repository is mocked: it hands the service's change the driver it "read" inside the transaction, and
 * the test keeps the outbox event the change returned.
 */
@ExtendWith(MockitoExtension.class)
class ReleaseMedicalBlockServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-22T21:00:00Z"), ZoneId.of("America/Bogota"));
    private static final OffsetDateTime EXPECTED_NOW =
            OffsetDateTime.parse("2026-10-22T16:00:00-05:00");
    private static final UUID DRIVER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CORRELATION_ID =
            UUID.fromString("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

    @Mock
    private DriverRepository repository;

    private ReleaseMedicalBlockService service;

    /** Event returned by the change the service passed to the repository. */
    private OutboxEvent returnedEvent;

    @BeforeEach
    void setUp() {
        service = new ReleaseMedicalBlockService(repository, CLOCK);
    }

    @Test
    void releaseWithMedicalBlockAndNoDocumentBlockLeavesActiveAndPublishesEvent() {
        Driver stored = driverWithBlocks(false, true);
        repositoryReads(stored, "Apto médico otorgado");

        Driver result = service.release(DRIVER_ID,
                new ReleaseMedicalBlock.Command("Apto médico otorgado", CORRELATION_ID));

        assertThat(result).isSameAs(stored);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(result.isMedicalBlock()).isFalse();
        assertThat(result.isDocumentBlock()).isFalse();
        assertThat(result.getUpdatedAt()).isEqualTo(EXPECTED_NOW);

        assertThat(returnedEvent).isNotNull();
        assertThat(returnedEvent.status()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(returnedEvent.attempts()).isZero();
        assertThat(returnedEvent.envelope().eventId()).isNotNull();
        assertThat(returnedEvent.envelope().eventType()).isEqualTo(DriverEventType.DRIVER_ACTIVATED.value());
        assertThat(returnedEvent.envelope().occurredAt()).isEqualTo(EXPECTED_NOW);
        assertThat(returnedEvent.envelope().source()).isEqualTo("driver");
        assertThat(returnedEvent.envelope().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(returnedEvent.envelope().data())
                .isEqualTo(new DriverActivatedPayload(result.getId(), result.getFullName()));

        verify(repository).releaseMedical(eq(DRIVER_ID), eq("Apto médico otorgado"), eq(EXPECTED_NOW), any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void releaseWithMedicalBlockAndDocumentBlockLeavesInactiveAndDoesNotPublishEvent() {
        Driver stored = driverWithBlocks(true, true);
        repositoryReads(stored, "Documentos aún vencidos");

        Driver result = service.release(DRIVER_ID,
                new ReleaseMedicalBlock.Command("Documentos aún vencidos", CORRELATION_ID));

        assertThat(result.getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(result.isMedicalBlock()).isFalse();
        assertThat(result.isDocumentBlock()).isTrue();
        assertThat(result.getUpdatedAt()).isEqualTo(EXPECTED_NOW);
        assertThat(returnedEvent).isNull();
    }

    @Test
    void releaseWithoutMedicalBlockThrowsStatusNotAllowedWithoutChangingTheDriver() {
        Driver stored = driverWithBlocks(false, false);
        repositoryReads(stored, "Notas");

        assertThatThrownBy(() -> service.release(DRIVER_ID, new ReleaseMedicalBlock.Command("Notas", CORRELATION_ID)))
                .isInstanceOf(StatusNotAllowedException.class)
                .hasMessage("El conductor no está inhabilitado por incidente");

        assertThat(stored.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(stored.isMedicalBlock()).isFalse();
    }

    @Test
    void releaseWithNonExistentDriverPropagatesDriverNotFound() {
        when(repository.releaseMedical(eq(DRIVER_ID), any(), any(), any()))
                .thenThrow(new DriverNotFoundException(DRIVER_ID));

        assertThatThrownBy(() -> service.release(DRIVER_ID, new ReleaseMedicalBlock.Command("Notas", CORRELATION_ID)))
                .isInstanceOf(DriverNotFoundException.class)
                .hasMessageContaining(DRIVER_ID.toString());
    }

    @Test
    void releaseWithNullNotesAndNullCorrelationIdGeneratesDefaults() {
        Driver stored = driverWithBlocks(false, true);
        when(repository.releaseMedical(eq(DRIVER_ID), isNull(), eq(EXPECTED_NOW), any()))
                .thenAnswer(invocation -> applyChange(invocation.getArgument(3), stored));

        Driver result = service.release(DRIVER_ID, new ReleaseMedicalBlock.Command(null, null));

        assertThat(result.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(returnedEvent).isNotNull();
        assertThat(returnedEvent.envelope().correlationId()).isNotNull();
    }

    @Test
    void commandSingleArgConstructorSetsRandomCorrelationId() {
        ReleaseMedicalBlock.Command command = new ReleaseMedicalBlock.Command("Nota simple");
        assertThat(command.notes()).isEqualTo("Nota simple");
        assertThat(command.correlationId()).isNotNull();
    }

    /** Makes the repository apply the service's change to {@code stored}, as the transaction would. */
    private void repositoryReads(Driver stored, String notes) {
        when(repository.releaseMedical(eq(DRIVER_ID), eq(notes), eq(EXPECTED_NOW), any()))
                .thenAnswer(invocation -> applyChange(invocation.getArgument(3), stored));
    }

    private Driver applyChange(Function<Driver, OutboxEvent> change, Driver stored) {
        returnedEvent = change.apply(stored);
        return stored;
    }

    private static Driver driverWithBlocks(boolean documentBlock, boolean medicalBlock) {
        OffsetDateTime created = OffsetDateTime.parse("2026-10-22T10:00:00-05:00");
        return Driver.restore(
                DRIVER_ID,
                "1144056789",
                "Carlos Pérez",
                "carlos@correo.com",
                "3001234567",
                documentBlock,
                medicalBlock,
                created,
                created);
    }
}
