package co.edu.univalle.conductores.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class FirestoreHealthIndicatorTest {

    private static final long TIMEOUT_SECONDS = 2;

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference collection;

    @Mock
    private Query query;

    @Mock
    private ApiFuture<QuerySnapshot> future;

    @Mock
    private QuerySnapshot snapshot;

    private FirestoreHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new FirestoreHealthIndicator(firestore);
        when(firestore.collection(DriverDocument.COLLECTION)).thenReturn(collection);
        when(collection.limit(1)).thenReturn(query);
        when(query.get()).thenReturn(future);
    }

    @Test
    void returnsUpWhenFirestoreResponds() throws Exception {
        when(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).thenReturn(snapshot);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void returnsDownWhenFirestoreFails() throws Exception {
        when(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .thenThrow(new ExecutionException(new RuntimeException("Firestore unavailable")));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void returnsDownWhenFirestoreTimesOut() throws Exception {
        when(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .thenThrow(new TimeoutException("Firestore timeout"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void returnsDownAndRestoresInterruptFlagWhenInterrupted() throws Exception {
        when(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("Interrupted"));

        try {
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
