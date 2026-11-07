package co.edu.univalle.conductores.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.firestore.Firestore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The Firestore client retry budget follows {@code app.firestore.timeout}.
 */
class FirestoreConfigTest {

    private static final Duration TIMEOUT = Duration.ofMillis(1500);

    @Test
    void retrySettingsAreBoundedByTheTimeout() {
        RetrySettings settings = FirestoreConfig.retrySettings(TIMEOUT);

        assertThat(settings.getTotalTimeoutDuration()).isEqualTo(TIMEOUT);
        assertThat(settings.getInitialRpcTimeoutDuration()).isEqualTo(TIMEOUT);
        assertThat(settings.getMaxRpcTimeoutDuration()).isEqualTo(TIMEOUT);
    }

    @Test
    void clientUsesTheConfiguredTimeout() throws Exception {
        try (Firestore firestore = new FirestoreConfig()
                .firestore("demo-conductores", "localhost:8080", new FirestoreProperties(TIMEOUT))) {
            assertThat(firestore.getOptions().getRetrySettings().getTotalTimeoutDuration()).isEqualTo(TIMEOUT);
        }
    }
}
