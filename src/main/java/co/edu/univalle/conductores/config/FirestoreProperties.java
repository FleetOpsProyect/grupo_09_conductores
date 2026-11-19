package co.edu.univalle.conductores.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firestore settings bound from {@code app.firestore.*}.
 *
 * @param timeout maximum wait for each Firestore call; also the client retry budget and the health
 *                check limit. Set with the {@code FIRESTORE_TIMEOUT} environment variable. Must be at
 *                least 1 ms, because the health check works in whole milliseconds.
 */
@ConfigurationProperties("app.firestore")
public record FirestoreProperties(Duration timeout) {

    /** Smallest accepted timeout. Shorter values would round down to 0 ms in the health check. */
    private static final Duration MIN_TIMEOUT = Duration.ofMillis(1);

    /**
     * Validates the settings at startup, so the application does not start with an invalid timeout.
     *
     * @throws IllegalArgumentException if the timeout is missing or shorter than 1 ms
     */
    public FirestoreProperties {
        if (timeout == null || timeout.compareTo(MIN_TIMEOUT) < 0) {
            throw new IllegalArgumentException(
                    "app.firestore.timeout (FIRESTORE_TIMEOUT) must be at least 1 ms, got: " + timeout);
        }
    }
}
