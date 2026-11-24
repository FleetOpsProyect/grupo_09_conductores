package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @BeforeEach
    void setUp() {
        service = new ReleaseMedicalBlockService(repository, CLOCK);
    }

    @Test
    void releaseWithMedicalBlockAndNoDocumentBlockLeavesActiveAndPublishesEvent() {
        Driver driver = driverWithBlocks(false, true);
        when(repository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        ReleaseMedicalBlock.Command command =
                new ReleaseMedicalBlock.Command("Apto médico otorgado", CORRELATION_ID);
        Driver result = service.release(DRIVER_ID, command);

        assertThat(result.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(result.isMedicalBlock()).isFalse();
        assertThat(result.isDocumentBlock()).isFalse();
        assertThat(result.getUpdatedAt()).isEqualTo(EXPECTED_NOW);

        ArgumentCaptor<Driver> driverCaptor = ArgumentCaptor.forClass(Driver.class);
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).saveMedicalRelease(
                driverCaptor.capture(),
                eq("Apto médico otorgado"),
                eq(EXPECTED_NOW),
                eventCaptor.capture());

        assertThat(driverCaptor.getValue()).isSameAs(result);
        OutboxEvent outboxEvent = eventCaptor.getValue();
        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.status()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(outboxEvent.attempts()).isZero();
        assertThat(outboxEvent.envelope().eventId()).isNotNull();
        assertThat(outboxEvent.envelope().eventType())
                .isEqualTo(DriverEventType.DRIVER_ACTIVATED.value());
        assertThat(outboxEvent.envelope().occurredAt()).isEqualTo(EXPECTED_NOW);
        assertThat(outboxEvent.envelope().source()).isEqualTo("driver");
        assertThat(outboxEvent.envelope().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(outboxEvent.envelope().data())
                .isEqualTo(new DriverActivatedPayload(result.getId(), result.getFullName()));

        verify(repository).findById(DRIVER_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void releaseWithMedicalBlockAndDocumentBlockLeavesInactiveAndDoesNotPublishEvent() {
        Driver driver = driverWithBlocks(true, true);
        when(repository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        ReleaseMedicalBlock.Command command =
                new ReleaseMedicalBlock.Command("Documentos aún vencidos", CORRELATION_ID);
        Driver result = service.release(DRIVER_ID, command);

        assertThat(result.getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(result.isMedicalBlock()).isFalse();
        assertThat(result.isDocumentBlock()).isTrue();
        assertThat(result.getUpdatedAt()).isEqualTo(EXPECTED_NOW);

        verify(repository).saveMedicalRelease(
                eq(result),
                eq("Documentos aún vencidos"),
                eq(EXPECTED_NOW),
                isNull());

        verify(repository).findById(DRIVER_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void releaseWithoutMedicalBlockThrowsStatusNotAllowedAndDoesNotSave() {
        Driver driver = driverWithBlocks(false, false);
        when(repository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        ReleaseMedicalBlock.Command command =
                new ReleaseMedicalBlock.Command("Notas", CORRELATION_ID);

        assertThatThrownBy(() -> service.release(DRIVER_ID, command))
                .isInstanceOf(StatusNotAllowedException.class)
                .hasMessage("El conductor no está inhabilitado por incidente");

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(driver.isMedicalBlock()).isFalse();

        verify(repository).findById(DRIVER_ID);
        verify(repository, never()).saveMedicalRelease(any(), any(), any(), any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void releaseWithNonExistentDriverThrowsDriverNotFound() {
        when(repository.findById(DRIVER_ID)).thenReturn(Optional.empty());

        ReleaseMedicalBlock.Command command =
                new ReleaseMedicalBlock.Command("Notas", CORRELATION_ID);

        assertThatThrownBy(() -> service.release(DRIVER_ID, command))
                .isInstanceOf(DriverNotFoundException.class)
                .hasMessageContaining(DRIVER_ID.toString());

        verify(repository).findById(DRIVER_ID);
        verify(repository, never()).saveMedicalRelease(any(), any(), any(), any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void releaseWithNullNotesAndNullCorrelationIdGeneratesDefaults() {
        Driver driver = driverWithBlocks(false, true);
        when(repository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        ReleaseMedicalBlock.Command command = new ReleaseMedicalBlock.Command(null, null);
        Driver result = service.release(DRIVER_ID, command);

        assertThat(result.getStatus()).isEqualTo(DriverStatus.ACTIVE);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).saveMedicalRelease(
                eq(result),
                isNull(),
                eq(EXPECTED_NOW),
                eventCaptor.capture());

        OutboxEvent outboxEvent = eventCaptor.getValue();
        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.envelope().correlationId()).isNotNull();

        verify(repository).findById(DRIVER_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void commandSingleArgConstructorSetsRandomCorrelationId() {
        ReleaseMedicalBlock.Command command = new ReleaseMedicalBlock.Command("Nota simple");
        assertThat(command.notes()).isEqualTo("Nota simple");
        assertThat(command.correlationId()).isNotNull();
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
