package co.edu.univalle.conductores.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.DriverUpdatedPayload;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.application.port.out.OutboxRepositoryPort;
import co.edu.univalle.conductores.application.port.out.ProcessedEventRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.PageResult;
import co.edu.univalle.conductores.domain.StatusNotAllowedException;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FirestoreDriverRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T16:00:00-05:00");

    @Autowired
    private DriverRepository repository;

    @Autowired
    private OutboxRepositoryPort outboxRepository;

    @Autowired
    private ProcessedEventRepositoryPort processedEventRepository;

    @Autowired
    private Firestore firestore;

    @Test
    void createsAndFindsDriver() {
        Driver driver = newDriver(uniqueDocument(), false);

        createDriverWithOutbox(driver);

        Driver found = repository.findById(driver.getId()).orElseThrow();
        assertThat(found.getDocumentNumber()).isEqualTo(driver.getDocumentNumber());
        assertThat(found.getStatus()).isEqualTo(DriverStatus.ACTIVE);
    }

    @Test
    void saveUpdatesStoredDriver() {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);

        driver.update("Nombre Actualizado", "nuevo@correo.com", (String) null, NOW.plusHours(1));
        repository.save(driver);

        assertThat(repository.findById(driver.getId()).orElseThrow().getFullName())
                .isEqualTo("Nombre Actualizado");
    }

    @Test
    void findAllFiltersByStatus() {
        Driver active = newDriver(uniqueDocument(), false);
        Driver blocked = newDriver(uniqueDocument(), true);
        createDriverWithOutbox(active);
        createDriverWithOutbox(blocked);

        PageResult<Driver> page = repository.findAll(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS, 0, 100);

        assertThat(page.content()).extracting(Driver::getId)
                .contains(blocked.getId())
                .doesNotContain(active.getId());
    }

    @Test
    void createsDriverAndStoresPendingOutboxEvent() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        OutboxEvent outboxEvent = createDriverWithOutbox(driver);

        Driver found = repository.findById(driver.getId()).orElseThrow();
        assertThat(found.getDocumentNumber()).isEqualTo(driver.getDocumentNumber());
        assertThat(found.getStatus()).isEqualTo(DriverStatus.ACTIVE);

        DocumentSnapshot outbox = firestore.collection(OutboxEventDocument.COLLECTION)
                .document(outboxEvent.envelope().eventId().toString())
                .get()
                .get();

        assertThat(outbox.exists()).isTrue();
        assertThat(outbox.getString(OutboxEventDocument.EVENT_TYPE))
                .isEqualTo(DriverEventType.DRIVER_ACTIVATED.value());
        assertThat(outbox.getString(OutboxEventDocument.STATUS))
                .isEqualTo(OutboxEventDocument.STATUS_PENDING);
        assertThat(outbox.getLong(OutboxEventDocument.ATTEMPTS)).isZero();
        assertThat(outbox.getString(OutboxEventDocument.PAYLOAD))
                .contains("\"event_type\":\"driver_activated_event\"");
    }

    @Test
    void duplicateDriverDoesNotStoreSecondOutboxEvent() throws Exception {
        String documentNumber = uniqueDocument();
        createDriverWithOutbox(newDriver(documentNumber, false));
        Driver second = newDriver(documentNumber, false);
        OutboxEvent secondEvent = pendingActivationEvent(second);

        assertThatThrownBy(() -> repository.createWithUniquenessAndOutbox(second, secondEvent))
                .isInstanceOf(DuplicateDocumentException.class);
        assertThat(repository.findById(second.getId())).isEmpty();

        DocumentSnapshot secondOutbox = firestore.collection(OutboxEventDocument.COLLECTION)
                .document(secondEvent.envelope().eventId().toString())
                .get()
                .get();
        assertThat(secondOutbox.exists()).isFalse();
    }

    @Test
    void pendingOutboxEventCanBeRetriedAndMarkedSent() throws Exception {
        long pendingBefore = outboxRepository.countPending();
        Driver driver = newDriver(uniqueDocument(), false);
        OutboxEvent outboxEvent = createDriverWithOutbox(driver);
        UUID eventId = outboxEvent.envelope().eventId();

        // Other tests leave pending events with the same created_at, so read every pending event, not a page.
        assertThat(outboxRepository.findPending(Math.toIntExact(pendingBefore) + 1))
                .extracting(event -> event.envelope().eventId())
                .contains(eventId);
        assertThat(outboxRepository.countPending()).isEqualTo(pendingBefore + 1);

        outboxRepository.recordPublishFailure(eventId);
        DocumentSnapshot failedAttempt = firestore.collection(OutboxEventDocument.COLLECTION)
                .document(eventId.toString())
                .get()
                .get();
        assertThat(failedAttempt.getLong(OutboxEventDocument.ATTEMPTS)).isEqualTo(1L);
        assertThat(failedAttempt.getString(OutboxEventDocument.STATUS))
                .isEqualTo(OutboxEventDocument.STATUS_PENDING);

        outboxRepository.markAsSent(eventId);

        assertThat(outboxRepository.findPending(10))
                .extracting(event -> event.envelope().eventId())
                .doesNotContain(eventId);
        assertThat(outboxRepository.countPending()).isEqualTo(pendingBefore);
    }

    @Test
    void appliesEventOnceAndStoresProcessedEventWithExpireAtInTheSameWrite() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        UUID eventId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-11-15T12:00:00Z");

        boolean applied = processedEventRepository.applyOnce(eventId, "document_expired_event", driver.getId(),
                processedAt, d -> d.applyDocumentBlock(NOW.plusHours(1)));

        assertThat(applied).isTrue();
        assertThat(repository.findById(driver.getId()).orElseThrow().getStatus())
                .isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        DocumentSnapshot processed = firestore.collection(ProcessedEventDocument.COLLECTION)
                .document(eventId.toString()).get().get();
        assertThat(processed.getString(ProcessedEventDocument.EVENT_ID)).isEqualTo(eventId.toString());
        assertThat(processed.getString(ProcessedEventDocument.EVENT_TYPE)).isEqualTo("document_expired_event");
        assertThat(processed.getString(ProcessedEventDocument.DRIVER_ID)).isEqualTo(driver.getId().toString());
        Instant storedProcessedAt = processed.getTimestamp(ProcessedEventDocument.PROCESSED_AT).toDate().toInstant();
        Instant storedExpireAt = processed.getTimestamp(ProcessedEventDocument.EXPIRE_AT).toDate().toInstant();
        assertThat(storedProcessedAt).isEqualTo(processedAt);
        assertThat(storedExpireAt).isEqualTo(processedAt.plus(Duration.ofDays(30)));
    }

    @Test
    void doesNotApplyAnEventTwice() {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        UUID eventId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-11-15T12:00:00Z");
        processedEventRepository.applyOnce(eventId, "document_expired_event", driver.getId(), processedAt,
                d -> d.applyDocumentBlock(NOW.plusHours(1)));

        boolean appliedAgain = processedEventRepository.applyOnce(eventId, "document_expired_event",
                driver.getId(), processedAt, d -> d.removeDocumentBlock(NOW.plusHours(2)));

        assertThat(appliedAgain).isFalse();
        assertThat(repository.findById(driver.getId()).orElseThrow().getStatus())
                .isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
    }

    @Test
    void throwsDriverNotFoundAndStoresNothingWhenDriverDoesNotExist() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID missingDriver = UUID.randomUUID();

        assertThatThrownBy(() -> processedEventRepository.applyOnce(eventId, "document_expired_event",
                missingDriver, Instant.now(), d -> d.applyDocumentBlock(NOW)))
                .isInstanceOf(DriverNotFoundException.class);

        assertThat(firestore.collection(ProcessedEventDocument.COLLECTION)
                .document(eventId.toString()).get().get().exists()).isFalse();
    }

    @Test
    void releaseMedicalWithActiveResultStoresNotesDateAndOutboxEvent() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        applyEvent(driver, "severe_incident_reported_event", d -> d.applyMedicalBlock(NOW.plusHours(1)));

        OutboxEvent[] stored = new OutboxEvent[1];
        Driver released = repository.releaseMedical(driver.getId(), "Conductor recuperado", NOW.plusHours(2), d -> {
            stored[0] = releaseAndActivate(d, NOW.plusHours(2));
            return stored[0];
        });

        assertThat(released.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        Driver found = repository.findById(driver.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(found.isMedicalBlock()).isFalse();

        DocumentSnapshot doc = driverDocument(driver);
        assertThat(doc.getString("medical_release_notes")).isEqualTo("Conductor recuperado");
        assertThat(doc.getString("medical_released_at")).isEqualTo(NOW.plusHours(2).toString());

        DocumentSnapshot outbox = firestore.collection(OutboxEventDocument.COLLECTION)
                .document(stored[0].envelope().eventId().toString()).get().get();
        assertThat(outbox.exists()).isTrue();
        assertThat(outbox.getString(OutboxEventDocument.EVENT_TYPE))
                .isEqualTo(DriverEventType.DRIVER_ACTIVATED.value());
    }

    @Test
    void releaseMedicalUsesTheDocumentBlockStoredWhenTheTransactionRuns() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        applyEvent(driver, "severe_incident_reported_event", d -> d.applyMedicalBlock(NOW.plusHours(1)));
        Driver readBeforeTheBlock = repository.findById(driver.getId()).orElseThrow();
        applyEvent(driver, "document_expired_event", d -> d.applyDocumentBlock(NOW.plusHours(2)));
        long pendingBefore = outboxRepository.countPending();

        Driver released = repository.releaseMedical(driver.getId(), "Apto médico", NOW.plusHours(3),
                d -> releaseAndActivate(d, NOW.plusHours(3)));

        assertThat(readBeforeTheBlock.isDocumentBlock()).isFalse();
        assertThat(released.getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        Driver found = repository.findById(driver.getId()).orElseThrow();
        assertThat(found.isDocumentBlock()).isTrue();
        assertThat(found.isMedicalBlock()).isFalse();
        assertThat(driverDocument(driver).getString("status")).isEqualTo("INACTIVE_EXPIRED_DOCUMENTS");
        assertThat(outboxRepository.countPending()).isEqualTo(pendingBefore);
    }

    @Test
    void releaseMedicalWithoutNotesStoresNullNotesAndValidDate() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        applyEvent(driver, "severe_incident_reported_event", d -> d.applyMedicalBlock(NOW.plusHours(1)));

        repository.releaseMedical(driver.getId(), null, NOW.plusHours(2), d -> releaseAndActivate(d, NOW.plusHours(2)));

        DocumentSnapshot doc = driverDocument(driver);
        assertThat(doc.getString("medical_release_notes")).isNull();
        assertThat(doc.getString("medical_released_at")).isEqualTo(NOW.plusHours(2).toString());
    }

    @Test
    void releaseMedicalWithoutMedicalBlockThrowsStatusNotAllowedAndStoresNothing() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);

        assertThatThrownBy(() -> repository.releaseMedical(driver.getId(), "Notas", NOW.plusHours(1),
                d -> releaseAndActivate(d, NOW.plusHours(1))))
                .isInstanceOf(StatusNotAllowedException.class);

        DocumentSnapshot doc = driverDocument(driver);
        assertThat(doc.contains("medical_release_notes")).isFalse();
        assertThat(doc.getString("updated_at")).isEqualTo(driver.getUpdatedAt().toString());
    }

    @Test
    void releaseMedicalThrowsDriverNotFoundWhenDriverDoesNotExist() {
        UUID missingDriver = UUID.randomUUID();

        assertThatThrownBy(() -> repository.releaseMedical(missingDriver, null, NOW, d -> null))
                .isInstanceOf(DriverNotFoundException.class);
    }

    @Test
    void applyOnceKeepsTheMedicalReleaseNotes() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        applyEvent(driver, "severe_incident_reported_event", d -> d.applyMedicalBlock(NOW.plusHours(1)));
        repository.releaseMedical(driver.getId(), "Conductor recuperado", NOW.plusHours(2),
                d -> releaseAndActivate(d, NOW.plusHours(2)));

        applyEvent(driver, "document_expired_event", d -> d.applyDocumentBlock(NOW.plusHours(3)));

        DocumentSnapshot doc = driverDocument(driver);
        assertThat(doc.getString("status")).isEqualTo("INACTIVE_EXPIRED_DOCUMENTS");
        assertThat(doc.getString("medical_release_notes")).isEqualTo("Conductor recuperado");
        assertThat(doc.getString("medical_released_at")).isEqualTo(NOW.plusHours(2).toString());
    }

    @Test
    void updateWithOutboxStoresNewDataAndEventAndKeepsMedicalReleaseFields() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        applyEvent(driver, "severe_incident_reported_event", d -> d.applyMedicalBlock(NOW.plusHours(1)));
        repository.releaseMedical(driver.getId(), "Conductor recuperado", NOW.plusHours(2),
                d -> releaseAndActivate(d, NOW.plusHours(2)));

        Driver current = repository.findById(driver.getId()).orElseThrow();
        current.update("Nombre Actualizado", "nuevo@correo.com", "3159876543", NOW.plusHours(3));
        OutboxEvent updated = pendingUpdateEvent(current);
        repository.updateWithOutbox(current, updated);

        Driver found = repository.findById(driver.getId()).orElseThrow();
        assertThat(found.getFullName()).isEqualTo("Nombre Actualizado");
        assertThat(found.getEmail()).isEqualTo("nuevo@correo.com");
        assertThat(found.getPhone()).isEqualTo("3159876543");
        assertThat(found.getStatus()).isEqualTo(DriverStatus.ACTIVE);

        DocumentSnapshot doc = driverDocument(driver);
        assertThat(doc.getString("updated_at")).isEqualTo(NOW.plusHours(3).toString());
        assertThat(doc.getString("medical_release_notes")).isEqualTo("Conductor recuperado");
        assertThat(doc.getString("medical_released_at")).isEqualTo(NOW.plusHours(2).toString());

        DocumentSnapshot outbox = firestore.collection(OutboxEventDocument.COLLECTION)
                .document(updated.envelope().eventId().toString()).get().get();
        assertThat(outbox.exists()).isTrue();
        assertThat(outbox.getString(OutboxEventDocument.EVENT_TYPE))
                .isEqualTo(DriverEventType.DRIVER_UPDATED.value());
    }

    @Test
    void updateWithOutboxKeepsABlockAppliedAfterTheDriverWasRead() throws Exception {
        Driver driver = newDriver(uniqueDocument(), false);
        createDriverWithOutbox(driver);
        Driver readBeforeTheBlock = repository.findById(driver.getId()).orElseThrow();
        applyEvent(driver, "document_expired_event", d -> d.applyDocumentBlock(NOW.plusHours(1)));

        readBeforeTheBlock.update("Nombre Actualizado", "nuevo@correo.com", null, NOW.plusHours(2));
        repository.updateWithOutbox(readBeforeTheBlock, pendingUpdateEvent(readBeforeTheBlock));

        Driver found = repository.findById(driver.getId()).orElseThrow();
        assertThat(found.getFullName()).isEqualTo("Nombre Actualizado");
        assertThat(found.isDocumentBlock()).isTrue();
        assertThat(found.getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(driverDocument(driver).getString("status")).isEqualTo("INACTIVE_EXPIRED_DOCUMENTS");
    }

    /** Applies a consumed event to the stored driver, as the SQS consumer does. */
    private void applyEvent(Driver driver, String eventType, Consumer<Driver> change) {
        processedEventRepository.applyOnce(UUID.randomUUID(), eventType, driver.getId(), Instant.now(), change);
    }

    /** The change ReleaseMedicalBlockService applies: release, and the activation event when ACTIVE. */
    private static OutboxEvent releaseAndActivate(Driver driver, OffsetDateTime at) {
        driver.releaseMedicalBlock(at);
        return driver.getStatus() == DriverStatus.ACTIVE ? pendingActivationEvent(driver) : null;
    }

    private DocumentSnapshot driverDocument(Driver driver) throws Exception {
        return firestore.collection(DriverDocument.COLLECTION).document(driver.getId().toString()).get().get();
    }

    private static OutboxEvent pendingUpdateEvent(Driver driver) {
        EventEnvelope<DriverUpdatedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                DriverEventType.DRIVER_UPDATED.value(),
                driver.getUpdatedAt(),
                "driver",
                UUID.randomUUID(),
                new DriverUpdatedPayload(driver.getId(), driver.getFullName()));
        return OutboxEvent.pending(envelope);
    }

    private OutboxEvent createDriverWithOutbox(Driver driver) {
        OutboxEvent outboxEvent = pendingActivationEvent(driver);
        repository.createWithUniquenessAndOutbox(driver, outboxEvent);
        return outboxEvent;
    }

    private static OutboxEvent pendingActivationEvent(Driver driver) {
        EventEnvelope<DriverActivatedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                DriverEventType.DRIVER_ACTIVATED.value(),
                driver.getCreatedAt(),
                "driver",
                UUID.randomUUID(),
                new DriverActivatedPayload(driver.getId(), driver.getFullName()));

        return OutboxEvent.pending(envelope);
    }

    private static Driver newDriver(String documentNumber, boolean documentBlock) {
        return Driver.restore(UUID.randomUUID(), documentNumber, "Carlos Pérez", "carlos@correo.com", null,
                documentBlock, false, NOW, NOW);
    }

    private static String uniqueDocument() {
        return String.format("%010d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }
}
