package co.edu.univalle.conductores.adapter.out.firestore;

import com.google.cloud.firestore.Firestore;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Health indicator that checks connectivity with Firestore.
 */
public class FirestoreHealthIndicator implements HealthIndicator {

    private final Firestore firestore;

    private final Duration timeout;

    /**
     * Creates the indicator.
     *
     * @param firestore Firestore client
     * @param timeout maximum time the check waits for Firestore
     */
    public FirestoreHealthIndicator(Firestore firestore, Duration timeout) {
        this.firestore = firestore;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Checks Firestore with a bounded read and no writes.
     *
     * @return UP if Firestore answers within the timeout, DOWN otherwise
     */
    @Override
    public Health health() {
        try {
            firestore.collection(DriverDocument.COLLECTION).limit(1).get()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return Health.up().build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.down(ex).build();
        } catch (ExecutionException | TimeoutException ex) {
            return Health.down(ex).build();
        }
    }
}
