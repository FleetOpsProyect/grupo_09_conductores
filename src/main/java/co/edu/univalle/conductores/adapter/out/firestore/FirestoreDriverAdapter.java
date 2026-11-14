package co.edu.univalle.conductores.adapter.out.firestore;

import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.application.port.out.OutboxRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.PageResult;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbound adapter: implements {@link DriverRepository} with Firestore.
 *
 * <p>Every Firestore call waits at most the configured timeout ({@code app.firestore.timeout}). Timeouts
 * and transient SDK errors become {@link PersistenceUnavailableException}; any other SDK error becomes
 * {@link IllegalStateException}. No SDK exception type leaves this class. This class does not log: the
 * web layer logs the failure once.
 */
public class FirestoreDriverAdapter implements DriverRepository, OutboxRepositoryPort {

    private static final String DOCUMENT_NUMBERS = "document_numbers";
    private static final Set<StatusCode.Code> TRANSIENT_CODES = EnumSet.of(
            StatusCode.Code.UNAVAILABLE,
            StatusCode.Code.DEADLINE_EXCEEDED,
            StatusCode.Code.RESOURCE_EXHAUSTED,
            StatusCode.Code.INTERNAL,
            StatusCode.Code.ABORTED,
            StatusCode.Code.CANCELLED,
            StatusCode.Code.UNKNOWN);

    private final Firestore firestore;
    private final Duration timeout;
    private final JsonMapper jsonMapper;

    /**
     * Creates the adapter.
     *
     * @param firestore Firestore client
     * @param timeout maximum time a request waits for Firestore
        * @param jsonMapper mapper configured by Spring
     */
    public FirestoreDriverAdapter(Firestore firestore, Duration timeout, JsonMapper jsonMapper) {
        this.firestore = firestore;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    /**
     * Guarda el conductor, reserva su número de documento y registra el evento
     * del outbox en una sola transacción.
     *
     * @param driver conductor a crear
     * @param outboxEvent evento que se publicará después del registro
     */
    @Override
    public void createWithUniquenessAndOutbox(Driver driver, OutboxEvent outboxEvent) {
        String documentNumber = driver.getDocumentNumber();
        DocumentReference driverRef = drivers().document(driver.getId().toString());
        DocumentReference outboxRef = firestore.collection(OutboxEventDocument.COLLECTION)
                .document(outboxEvent.envelope().eventId().toString());

        await(firestore.runTransaction(transaction -> {
            DocumentReference numberRef = firestore.collection(DOCUMENT_NUMBERS).document(documentNumber);
            if (transaction.get(numberRef).get().exists()) {
                throw new DuplicateDocumentException(documentNumber);
            }

            transaction.set(numberRef, Map.of("driver_id", driver.getId().toString()));
            transaction.set(driverRef, DriverDocument.fromDomain(driver));
            transaction.set(outboxRef, OutboxEventDocument.fromDomain(outboxEvent, jsonMapper));
            return null;
        }));
    }

    /**
     * Finds pending outbox events in creation order.
     *
     * @param limit maximum number of events to return
     * @return pending outbox events
     */
    @Override
    public List<OutboxEvent> findPending(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit debe ser mayor que cero");
        }
        Query query = outboxEvents()
                .whereEqualTo(OutboxEventDocument.STATUS, OutboxEventDocument.STATUS_PENDING)
                .orderBy(OutboxEventDocument.CREATED_AT)
                .limit(limit);
        return await(query.get()).getDocuments().stream()
                .map(document -> OutboxEventDocument.toDomain(document.getData(), jsonMapper))
                .toList();
    }

    /**
     * Counts all pending outbox events.
     *
     * @return number of pending events
     */
    @Override
    public long countPending() {
        return await(outboxEvents()
                .whereEqualTo(OutboxEventDocument.STATUS, OutboxEventDocument.STATUS_PENDING)
                .count()
                .get())
                .getCount();
    }

    /**
     * Marks an SNS-confirmed event as sent.
     *
     * @param eventId identifier of the event
     */
    @Override
    public void markAsSent(UUID eventId) {
        await(outboxEvents().document(eventId.toString()).update(
                OutboxEventDocument.STATUS, OutboxEventDocument.STATUS_SENT));
    }

    /**
     * Increments the failed publication attempts of an event.
     *
     * @param eventId identifier of the event
     */
    @Override
    public void recordPublishFailure(UUID eventId) {
        await(outboxEvents().document(eventId.toString()).update(
                OutboxEventDocument.ATTEMPTS, FieldValue.increment(1)));
    }

    @Override
    public void save(Driver driver) {
        await(drivers().document(driver.getId().toString()).set(DriverDocument.fromDomain(driver)));
    }

    @Override
    public Optional<Driver> findById(UUID id) {
        DocumentSnapshot snapshot = await(drivers().document(id.toString()).get());
        return snapshot.exists()
                ? Optional.of(DriverDocument.toDomain(snapshot.getData()))
                : Optional.empty();
    }

    /**
     * Lists drivers. The count and the page queries run in parallel and share one deadline, so the
     * whole call also waits at most the configured timeout. If the count fails, the page query is cancelled.
     */
    @Override
    public PageResult<Driver> findAll(DriverStatus status, int page, int size) {
        Query query = drivers().orderBy("created_at");
        if (status != null) {
            query = query.whereEqualTo("status", status.name());
        }
        long deadline = deadline();
        ApiFuture<AggregateQuerySnapshot> countFuture = query.count().get();
        ApiFuture<QuerySnapshot> pageFuture = query.offset(page * size).limit(size).get();
        AggregateQuerySnapshot count;
        try {
            count = await(countFuture, deadline);
        } catch (RuntimeException ex) {
            pageFuture.cancel(true);
            throw ex;
        }
        QuerySnapshot snapshot = await(pageFuture, deadline);
        List<Driver> content = snapshot.getDocuments().stream()
                .map(document -> DriverDocument.toDomain(document.getData()))
                .toList();
        return new PageResult<>(content, page, size, count.getCount());
    }

    private CollectionReference drivers() {
        return firestore.collection(DriverDocument.COLLECTION);
    }

    private CollectionReference outboxEvents() {
        return firestore.collection(OutboxEventDocument.COLLECTION);
    }

    private long deadline() {
        return System.nanoTime() + timeout.toNanos();
    }

    private <T> T await(Future<T> future) {
        return await(future, deadline());
    }

    private <T> T await(Future<T> future, long deadline) {
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new PersistenceUnavailableException("Firestore did not answer within " + timeout, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new PersistenceUnavailableException("Firestore call interrupted", ex);
        } catch (ExecutionException ex) {
            throw translate(ex.getCause());
        }
    }

    /**
     * Maps the failure of a Firestore call to a non-SDK exception.
     *
     * @param failure cause reported by the SDK future
     * @return the domain exception to rethrow
     */
    private static RuntimeException translate(Throwable failure) {
        if (failure instanceof DuplicateDocumentException duplicate) {
            return duplicate;
        }
        ApiException api = findApiException(failure);
        if (api != null && TRANSIENT_CODES.contains(api.getStatusCode().getCode())) {
            return new PersistenceUnavailableException(
                    "Firestore unavailable: " + api.getStatusCode().getCode(), failure);
        }
        return new IllegalStateException("Firestore call failed", failure);
    }

    private static ApiException findApiException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ApiException api) {
                return api;
            }
        }
        return null;
    }
}
