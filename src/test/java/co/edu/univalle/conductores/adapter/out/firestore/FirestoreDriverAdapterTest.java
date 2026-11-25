package co.edu.univalle.conductores.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.PageResult;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import com.google.api.core.ApiFuture;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.Transaction;
import io.grpc.Status;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests of the Firestore failure translation (TEC_04-4), with the SDK mocked.
 */
@ExtendWith(MockitoExtension.class)
class FirestoreDriverAdapterTest {

    private static final Driver DRIVER = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com",
            null, OffsetDateTime.of(2026, 11, 5, 10, 0, 0, 0, ZoneOffset.ofHours(-5)));

    /** Repository operations that call Firestore. */
    enum Operation { CREATE, FIND_BY_ID, FIND_ALL, SAVE, RELEASE_MEDICAL, UPDATE_WITH_OUTBOX }

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference drivers;

    @Mock
    private DocumentReference document;

    @Mock
    private Query query;

    @Mock
    private AggregateQuery countQuery;

    @Mock
    private CollectionReference outboxEvents;

    @Mock
    private DocumentReference outboxDocument;

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private ApiFuture<QuerySnapshot> pageFuture;

    private FirestoreDriverAdapter adapter;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        adapter = new FirestoreDriverAdapter(firestore, TIMEOUT, JSON_MAPPER);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void unavailableBecomesPersistenceUnavailable(Operation operation) throws Exception {
        ApiFuture<Object> future = stub(operation);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(apiException(Status.Code.UNAVAILABLE)));

        Throwable thrown = catchThrowable(() -> invoke(operation));

        assertThat(thrown).isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("UNAVAILABLE");
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void deadlineExceededBecomesPersistenceUnavailable(Operation operation) throws Exception {
        ApiFuture<Object> future = stub(operation);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(apiException(Status.Code.DEADLINE_EXCEEDED)));

        assertThat(catchThrowable(() -> invoke(operation))).isInstanceOf(PersistenceUnavailableException.class);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void timeoutBecomesPersistenceUnavailableAndCancelsTheCall(Operation operation) throws Exception {
        ApiFuture<Object> future = stub(operation);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new TimeoutException());

        assertThat(catchThrowable(() -> invoke(operation))).isInstanceOf(PersistenceUnavailableException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        verify(future).cancel(true);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void waitsAtMostTwoSeconds(Operation operation) throws Exception {
        ApiFuture<Object> future = stub(operation);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new TimeoutException());
        ArgumentCaptor<Long> timeout = ArgumentCaptor.forClass(Long.class);

        catchThrowable(() -> invoke(operation));

        verify(future).get(timeout.capture(), eq(TimeUnit.NANOSECONDS));
        assertThat(timeout.getValue()).isPositive().isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(2));
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void waitsAtMostTheConfiguredTimeout(Operation operation) throws Exception {
        adapter = new FirestoreDriverAdapter(firestore, Duration.ofMillis(500), JSON_MAPPER);
        ApiFuture<Object> future = stub(operation);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new TimeoutException());
        ArgumentCaptor<Long> timeout = ArgumentCaptor.forClass(Long.class);

        Throwable thrown = catchThrowable(() -> invoke(operation));

        verify(future).get(timeout.capture(), eq(TimeUnit.NANOSECONDS));
        assertThat(timeout.getValue()).isPositive().isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(500));
        assertThat(thrown).isInstanceOf(PersistenceUnavailableException.class).hasMessageContaining("PT0.5S");
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class,
            names = {"RESOURCE_EXHAUSTED", "INTERNAL", "ABORTED", "CANCELLED", "UNKNOWN"})
    void otherTransientCodesBecomePersistenceUnavailable(Status.Code code) throws Exception {
        ApiFuture<Object> future = stub(Operation.FIND_BY_ID);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(apiException(code)));

        assertThat(catchThrowable(() -> invoke(Operation.FIND_BY_ID)))
                .isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void wrappedUnavailableIsFoundInTheCauseChain() throws Exception {
        ApiFuture<Object> future = stub(Operation.SAVE);
        RuntimeException wrapper = new RuntimeException("wrapper", apiException(Status.Code.UNAVAILABLE));
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new ExecutionException(wrapper));

        assertThat(catchThrowable(() -> invoke(Operation.SAVE))).isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void nonTransientSdkErrorBecomesIllegalStateNotSdkType() throws Exception {
        ApiFuture<Object> future = stub(Operation.FIND_BY_ID);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(apiException(Status.Code.PERMISSION_DENIED)));

        Throwable thrown = catchThrowable(() -> invoke(Operation.FIND_BY_ID));

        assertThat(thrown).isExactlyInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ApiException.class)
                .isNotInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void unknownFailureBecomesIllegalState() throws Exception {
        ApiFuture<Object> future = stub(Operation.FIND_BY_ID);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(new RuntimeException("boom")));

        assertThat(catchThrowable(() -> invoke(Operation.FIND_BY_ID))).isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void interruptionBecomesPersistenceUnavailableAndRestoresTheFlag() throws Exception {
        ApiFuture<Object> future = stub(Operation.FIND_BY_ID);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new InterruptedException());

        try {
            assertThat(catchThrowable(() -> invoke(Operation.FIND_BY_ID)))
                    .isInstanceOf(PersistenceUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(future).cancel(true);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void duplicateDocumentIsKept() throws Exception {
        ApiFuture<Object> future = stub(Operation.CREATE);
        DuplicateDocumentException duplicate = new DuplicateDocumentException("1144056789");
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new ExecutionException(duplicate));

        assertThat(catchThrowable(() -> invoke(Operation.CREATE))).isSameAs(duplicate);
    }

    @Test
    void nonDuplicateCreateSdkErrorIsNotReportedAsDuplicate() throws Exception {
        ApiFuture<Object> future = stub(Operation.CREATE);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(apiException(Status.Code.PERMISSION_DENIED)));

        Throwable thrown = catchThrowable(() -> invoke(Operation.CREATE));

        assertThat(thrown).isExactlyInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void unknownCreateFailureIsNotReportedAsDuplicate() throws Exception {
        ApiFuture<Object> future = stub(Operation.CREATE);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(new RuntimeException("boom")));

        Throwable thrown = catchThrowable(() -> invoke(Operation.CREATE));

        assertThat(thrown).isExactlyInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void findByIdReturnsEmptyOnlyWhenFirestoreAnswersThatTheDocumentIsMissing() throws Exception {
        ApiFuture<Object> future = stub(Operation.FIND_BY_ID);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(snapshot);

        assertThat(adapter.findById(DRIVER.getId())).isEmpty();
    }

    @Test
    void findAllCancelsThePageQueryWhenTheCountFails() throws Exception {
        ApiFuture<Object> countFuture = stub(Operation.FIND_ALL);
        when(countFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(apiException(Status.Code.UNAVAILABLE)));

        assertThat(catchThrowable(() -> invoke(Operation.FIND_ALL)))
                .isInstanceOf(PersistenceUnavailableException.class);
        verify(pageFuture).cancel(true);
        verify(pageFuture, never()).get(anyLong(), any(TimeUnit.class));
    }

    @Test
    void findAllCancelsThePageQueryWhenItTimesOut() throws Exception {
        ApiFuture<Object> countFuture = stub(Operation.FIND_ALL);
        when(countFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(mock(AggregateQuerySnapshot.class));
        when(pageFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new TimeoutException());

        assertThat(catchThrowable(() -> invoke(Operation.FIND_ALL)))
                .isInstanceOf(PersistenceUnavailableException.class);
        verify(pageFuture).cancel(true);
    }

    @Test
    void findAllReturnsThePageWhenFirestoreAnswers() throws Exception {
        ApiFuture<Object> countFuture = stub(Operation.FIND_ALL);
        AggregateQuerySnapshot count = mock(AggregateQuerySnapshot.class);
        when(count.getCount()).thenReturn(0L);
        QuerySnapshot page = mock(QuerySnapshot.class);
        when(page.getDocuments()).thenReturn(List.of());
        when(countFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(count);
        when(pageFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(page);

        PageResult<Driver> result = adapter.findAll(null, 0, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void releaseMedicalReadsTheDriverInTheTransactionAndWritesDriverAndOutbox() throws Exception {
        when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
        when(drivers.document(DRIVER.getId().toString())).thenReturn(document);
        when(firestore.collection(OutboxEventDocument.COLLECTION)).thenReturn(outboxEvents);
        when(outboxEvents.document(any(String.class))).thenReturn(outboxDocument);
        ArgumentCaptor<Transaction.Function> functionCaptor = captureTransaction();

        OffsetDateTime releasedAt = OffsetDateTime.of(2026, 11, 18, 14, 0, 0, 0, ZoneOffset.ofHours(-5));
        Driver[] received = new Driver[1];
        adapter.releaseMedical(DRIVER.getId(), "Alta médica completa", releasedAt, driver -> {
            received[0] = driver;
            return pendingActivationEvent();
        });

        Transaction transaction = transactionReading(storedDriverWithMedicalBlock());
        Object result = functionCaptor.getValue().updateCallback(transaction);

        assertThat(received[0].isMedicalBlock()).isTrue();
        assertThat(result).isSameAs(received[0]);
        ArgumentCaptor<Map<String, Object>> driverDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transaction).set(eq(document), driverDataCaptor.capture());
        Map<String, Object> data = driverDataCaptor.getValue();
        assertThat(data.get("medical_release_notes")).isEqualTo("Alta médica completa");
        assertThat(data.get("medical_released_at")).isEqualTo(releasedAt.toString());
        verify(transaction).set(eq(outboxDocument), anyMap());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void releaseMedicalWithoutOutboxDoesNotWriteOutboxInTransaction() throws Exception {
        when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
        when(drivers.document(DRIVER.getId().toString())).thenReturn(document);
        ArgumentCaptor<Transaction.Function> functionCaptor = captureTransaction();

        OffsetDateTime releasedAt = OffsetDateTime.of(2026, 11, 18, 14, 0, 0, 0, ZoneOffset.ofHours(-5));
        adapter.releaseMedical(DRIVER.getId(), null, releasedAt, driver -> null);

        Transaction transaction = transactionReading(storedDriverWithMedicalBlock());
        functionCaptor.getValue().updateCallback(transaction);

        ArgumentCaptor<Map<String, Object>> driverDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transaction).set(eq(document), driverDataCaptor.capture());
        Map<String, Object> data = driverDataCaptor.getValue();
        assertThat(data.get("medical_release_notes")).isNull();
        assertThat(data.get("medical_released_at")).isEqualTo(releasedAt.toString());
        verify(firestore, never()).collection(OutboxEventDocument.COLLECTION);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void releaseMedicalThrowsDriverNotFoundWhenTheTransactionFindsNoDriver() throws Exception {
        when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
        when(drivers.document(DRIVER.getId().toString())).thenReturn(document);
        ArgumentCaptor<Transaction.Function> functionCaptor = captureTransaction();
        adapter.releaseMedical(DRIVER.getId(), null, OffsetDateTime.now(), driver -> null);

        Transaction transaction = transactionReading(null);

        assertThat(catchThrowable(() -> functionCaptor.getValue().updateCallback(transaction)))
                .isInstanceOf(DriverNotFoundException.class);
        verify(transaction, never()).set(any(DocumentReference.class), anyMap());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateWithOutboxWritesOnlyTheEditableFields() throws Exception {
        when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
        when(drivers.document(DRIVER.getId().toString())).thenReturn(document);
        when(firestore.collection(OutboxEventDocument.COLLECTION)).thenReturn(outboxEvents);
        when(outboxEvents.document(any(String.class))).thenReturn(outboxDocument);
        ArgumentCaptor<Transaction.Function> functionCaptor = captureTransaction();

        adapter.updateWithOutbox(DRIVER, pendingActivationEvent());
        Transaction transaction = mock(Transaction.class);
        functionCaptor.getValue().updateCallback(transaction);

        ArgumentCaptor<Map<String, Object>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transaction).update(eq(document), fieldsCaptor.capture());
        assertThat(fieldsCaptor.getValue()).containsOnlyKeys("full_name", "email", "phone", "updated_at");
        verify(transaction, never()).set(eq(document), anyMap());
        verify(transaction).set(eq(outboxDocument), anyMap());
    }

    /** Captures the function the adapter passes to runTransaction; the future answers right away. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Transaction.Function> captureTransaction() throws Exception {
        ArgumentCaptor<Transaction.Function> functionCaptor = ArgumentCaptor.forClass(Transaction.Function.class);
        ApiFuture future = mock(ApiFuture.class);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(null);
        when(firestore.runTransaction(functionCaptor.capture())).thenReturn(future);
        return functionCaptor;
    }

    /** A transaction whose read of the driver returns {@code stored}, or a missing document when null. */
    @SuppressWarnings("unchecked")
    private Transaction transactionReading(Map<String, Object> stored) throws Exception {
        Transaction transaction = mock(Transaction.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        when(snapshot.exists()).thenReturn(stored != null);
        if (stored != null) {
            when(snapshot.getData()).thenReturn(stored);
        }
        ApiFuture<DocumentSnapshot> read = mock(ApiFuture.class);
        when(read.get()).thenReturn(snapshot);
        when(transaction.get(document)).thenReturn(read);
        return transaction;
    }

    private static Map<String, Object> storedDriverWithMedicalBlock() {
        Driver blocked = Driver.restore(DRIVER.getId(), DRIVER.getDocumentNumber(), DRIVER.getFullName(),
                DRIVER.getEmail(), null, false, true, DRIVER.getCreatedAt(), DRIVER.getCreatedAt());
        return DriverDocument.fromDomain(blocked);
    }

    /**
     * Stubs the SDK chain of the operation and returns the first future the adapter waits for.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ApiFuture<Object> stub(Operation operation) {
        ApiFuture<Object> future = mock(ApiFuture.class);
        switch (operation) {
            case CREATE, UPDATE_WITH_OUTBOX -> {
                when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
                when(drivers.document(DRIVER.getId().toString())).thenReturn(document);
                when(firestore.collection(OutboxEventDocument.COLLECTION)).thenReturn(outboxEvents);
                when(outboxEvents.document(any(String.class))).thenReturn(outboxDocument);
                when(firestore.runTransaction(any(Transaction.Function.class)))
                        .thenReturn((ApiFuture) future);
            }
            case FIND_BY_ID -> {
                stubDocument();
                when(document.get()).thenReturn((ApiFuture) future);
            }
            case SAVE -> {
                stubDocument();
                when(document.set(anyMap())).thenReturn((ApiFuture) future);
            }
            case FIND_ALL -> {
                when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
                when(drivers.orderBy("created_at")).thenReturn(query);
                when(query.orderBy(any(FieldPath.class), any(Query.Direction.class))).thenReturn(query);
                when(query.count()).thenReturn(countQuery);
                when(countQuery.get()).thenReturn((ApiFuture) future);
                stubPageQuery();
            }
            case RELEASE_MEDICAL -> {
                when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
                when(drivers.document(DRIVER.getId().toString())).thenReturn(document);
                when(firestore.runTransaction(any(Transaction.Function.class)))
                        .thenReturn((ApiFuture) future);
            }
        }
        return future;
    }

    @SuppressWarnings("unchecked")
    private void stubPageQuery() {
        pageFuture = mock(ApiFuture.class);
        when(query.offset(0)).thenReturn(query);
        when(query.limit(10)).thenReturn(query);
        when(query.get()).thenReturn(pageFuture);
    }

    private void stubDocument() {
        when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(drivers);
        when(drivers.document(DRIVER.getId().toString())).thenReturn(document);
    }

    private void invoke(Operation operation) {
        switch (operation) {
            case CREATE -> adapter.createWithUniquenessAndOutbox(DRIVER, pendingActivationEvent());
            case FIND_BY_ID -> adapter.findById(DRIVER.getId());
            case SAVE -> adapter.save(DRIVER);
            case FIND_ALL -> adapter.findAll(null, 0, 10);
            case RELEASE_MEDICAL -> adapter.releaseMedical(
                    DRIVER.getId(), "Notas de prueba", OffsetDateTime.now(), driver -> pendingActivationEvent());
            case UPDATE_WITH_OUTBOX -> adapter.updateWithOutbox(DRIVER, pendingActivationEvent());
        }
    }

    private static ApiException apiException(Status.Code code) {
        return ApiExceptionFactory.createException(new RuntimeException("sdk detail " + UUID.randomUUID()),
                GrpcStatusCode.of(code), false);
    }

    private static OutboxEvent pendingActivationEvent() {
        EventEnvelope<DriverActivatedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                DriverEventType.DRIVER_ACTIVATED.value(),
                DRIVER.getCreatedAt(),
                "driver",
                UUID.randomUUID(),
                new DriverActivatedPayload(DRIVER.getId(), DRIVER.getFullName()));

        return OutboxEvent.pending(envelope);
    }
}
