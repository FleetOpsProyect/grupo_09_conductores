package co.edu.univalle.conductores.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.application.port.out.OutboxRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.PageResult;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FirestoreDriverRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T16:00:00-05:00");

    @Autowired
    private DriverRepository repository;

    @Autowired
    private OutboxRepositoryPort outboxRepository;

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

        driver.update("Nombre Actualizado", "nuevo@correo.com", null, NOW.plusHours(1));
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

        assertThat(outboxRepository.findPending(10))
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
