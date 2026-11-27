package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverBlockedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.Command;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.EventProcessingResult;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.ProcessedEventRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDriverEventServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-14T10:00:00-05:00");
    private static final Instant PROCESSED_AT = Instant.parse("2026-11-15T12:00:00Z");
    private static final UUID DRIVER_ID = UUID.fromString("96b65376-7878-4390-bfba-cbdfa67b57c6");
    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID CORRELATION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Mock
    private ConsumerMetricsPort consumerMetrics;

    private InMemoryProcessedEvents repository;
    private ProcessDriverEventService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProcessedEvents();
        service = new ProcessDriverEventService(repository, consumerMetrics, Clock.fixed(PROCESSED_AT, ZoneOffset.UTC));
    }

    @Test
    void rejectsNullDependencies() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new ProcessDriverEventService(null, consumerMetrics, clock))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProcessDriverEventService(repository, null, clock))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProcessDriverEventService(repository, consumerMetrics, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void processesDocumentExpiredEventAndAppliesBlock() {
        Driver driver = repository.add(activeDriver());
        OffsetDateTime expiredAt = NOW.plusHours(1);

        EventProcessingResult result = service.process(command(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT,
                expiredAt));

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isDocumentBlock()).isTrue();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(driver.getUpdatedAt()).isEqualTo(expiredAt);
        assertThat(repository.processedAt).isEqualTo(PROCESSED_AT);
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, "processed");
    }

    @Test
    void documentExpiredOfAnActiveDriverReturnsDriverBlockedEventForTheOutbox() {
        Driver driver = repository.add(activeDriver());

        service.process(command(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, NOW.plusHours(1)));

        OutboxEvent event = repository.outbox;
        assertThat(event).isNotNull();
        assertThat(event.status()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.envelope().eventId()).isNotNull().isNotEqualTo(EVENT_ID);
        assertThat(event.envelope().eventType()).isEqualTo(DriverEventType.DRIVER_BLOCKED.value());
        assertThat(event.envelope().source()).isEqualTo("driver");
        assertThat(event.envelope().occurredAt().toInstant()).isEqualTo(PROCESSED_AT);
        assertThat(event.envelope().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(event.envelope().data()).isEqualTo(new DriverBlockedPayload(DRIVER_ID, driver.getFullName()));
    }

    @Test
    void documentExpiredOfADriverAlreadyInactiveForDocumentsReturnsNoEvent() {
        Driver driver = repository.add(Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com",
                null, true, false, NOW, NOW));

        service.process(command(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, NOW.plusHours(1)));

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(repository.outbox).isNull();
    }

    @Test
    void documentExpiredOfADriverOnMedicalHoldKeepsTheHoldAndReturnsNoEvent() {
        Driver driver = repository.add(Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com",
                null, false, true, NOW, NOW));

        service.process(command(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, NOW.plusHours(1)));

        assertThat(driver.isDocumentBlock()).isTrue();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.MEDICAL_HOLD_INCIDENT);
        assertThat(repository.outbox).isNull();
    }

    @Test
    void processesDocumentRenewedEventAndRemovesBlock() {
        Driver driver = repository.add(Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com",
                null, true, false, NOW, NOW));
        OffsetDateTime renewedAt = NOW.plusHours(2);

        EventProcessingResult result = service.process(command(ProcessDriverEventService.DOCUMENT_RENEWED_EVENT,
                renewedAt));

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isDocumentBlock()).isFalse();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(driver.getUpdatedAt()).isEqualTo(renewedAt);
        assertThat(repository.outbox).isNotNull();
        assertThat(repository.outbox.envelope().eventType()).isEqualTo(DriverEventType.DRIVER_ACTIVATED.value());
        assertThat(repository.outbox.envelope().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(repository.outbox.envelope().data())
                .isEqualTo(new DriverActivatedPayload(DRIVER_ID, driver.getFullName()));
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.DOCUMENT_RENEWED_EVENT, "processed");
    }

    @Test
    void renewedEventWithMedicalHoldKeepsTheHoldAndStoresNoEvent() {
        Driver driver = repository.add(Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com",
                null, true, true, NOW, NOW));

        EventProcessingResult result = service.process(command(ProcessDriverEventService.DOCUMENT_RENEWED_EVENT,
                NOW.plusHours(2)));

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isDocumentBlock()).isFalse();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.MEDICAL_HOLD_INCIDENT);
        assertThat(repository.outbox).isNull();
    }

    @Test
    void renewedEventWithoutDocumentBlockReturnsProcessedWithoutChange() {
        Driver driver = repository.add(Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com",
                null, false, false, NOW, NOW));

        EventProcessingResult result = service.process(command(ProcessDriverEventService.DOCUMENT_RENEWED_EVENT,
                NOW.plusHours(2)));

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED_WITHOUT_CHANGE);
        assertThat(driver.getUpdatedAt()).isEqualTo(NOW);
        assertThat(repository.outbox).isNull();
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.DOCUMENT_RENEWED_EVENT, "processed");
    }

    @Test
    void incidentOnAnActiveDriverAppliesMedicalBlockAndReturnsBlockedEventForOutbox() {
        Driver driver = repository.add(activeDriver());
        OffsetDateTime incidentAt = NOW.plusHours(1);

        EventProcessingResult result = service.process(command(
                ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT, incidentAt));

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isMedicalBlock()).isTrue();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.MEDICAL_HOLD_INCIDENT);
        assertThat(repository.outbox).isNotNull();
        assertThat(repository.outbox.status()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(repository.outbox.envelope().eventType()).isEqualTo(DriverEventType.DRIVER_BLOCKED.value());
        assertThat(repository.outbox.envelope().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(repository.outbox.envelope().data())
                .isEqualTo(new DriverBlockedPayload(DRIVER_ID, driver.getFullName()));
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT, "processed");
    }

    @Test
    void incidentOnADriverAlreadyBlockedPreservesDocumentBlockAndReturnsNoEvent() {
        Driver driver = repository.add(Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com",
                null, true, false, NOW, NOW));

        EventProcessingResult result = service.process(command(
                ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT, NOW.plusHours(1)));

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isDocumentBlock()).isTrue();
        assertThat(driver.isMedicalBlock()).isTrue();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.MEDICAL_HOLD_INCIDENT);
        assertThat(repository.outbox).isNull();
    }

    @Test
    void duplicateIncidentEventDoesNotApplyAnotherChangeOrCreateAnOutboxEvent() {
        Driver driver = repository.add(activeDriver());
        repository.processed.add(EVENT_ID);

        EventProcessingResult result = service.process(command(
                ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT, NOW.plusHours(1)));

        assertThat(result).isEqualTo(EventProcessingResult.DUPLICATE);
        assertThat(driver.isMedicalBlock()).isFalse();
        assertThat(repository.outbox).isNull();
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT, "duplicate");
    }

    @Test
    void returnsDuplicateWithoutChangesWhenEventAlreadyProcessed() {
        Driver driver = repository.add(activeDriver());
        repository.processed.add(EVENT_ID);

        EventProcessingResult result = service.process(command(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT,
                NOW.plusHours(1)));

        assertThat(result).isEqualTo(EventProcessingResult.DUPLICATE);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, "duplicate");
    }

    @Test
    void propagatesDriverNotFound() {
        Command command = command(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, NOW);

        assertThatThrownBy(() -> service.process(command)).isInstanceOf(DriverNotFoundException.class);

        assertThat(repository.processed).isEmpty();
        verify(consumerMetrics, never()).recordConsumed(any(), any());
    }

    @Test
    void rejectsUnsupportedEventTypeBeforeTouchingTheRepository() {
        Command command = command("unknown_event", NOW);

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de evento no soportado");

        assertThat(repository.calls).isZero();
        verify(consumerMetrics, never()).recordConsumed(any(), any());
    }

    private static Driver activeDriver() {
        return Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com", null,
                false, false, NOW, NOW);
    }

    private static Command command(String eventType, OffsetDateTime occurredAt) {
        return new Command(EVENT_ID, eventType, DRIVER_ID, occurredAt, CORRELATION_ID);
    }

    /** Fake that mirrors the transactional contract of the Firestore adapter. */
    private static final class InMemoryProcessedEvents implements ProcessedEventRepositoryPort {

        private final Map<UUID, Driver> drivers = new HashMap<>();
        private final Set<UUID> processed = new HashSet<>();
        private Instant processedAt;
        private OutboxEvent outbox;
        private int calls;

        Driver add(Driver driver) {
            drivers.put(driver.getId(), driver);
            return driver;
        }

        @Override
        public boolean applyOnce(UUID eventId, String eventType, UUID driverId, Instant processedAt,
                Function<Driver, OutboxEvent> change) {
            calls++;
            if (processed.contains(eventId)) {
                return false;
            }
            Driver driver = drivers.get(driverId);
            if (driver == null) {
                throw new DriverNotFoundException(driverId);
            }
            outbox = change.apply(driver);
            processed.add(eventId);
            this.processedAt = processedAt;
            return true;
        }
    }
}
