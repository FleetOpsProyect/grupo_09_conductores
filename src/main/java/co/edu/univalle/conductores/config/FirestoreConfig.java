package co.edu.univalle.conductores.config;

import co.edu.univalle.conductores.adapter.out.firestore.FirestoreDriverAdapter;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Firestore client configuration.
 */
@Configuration
public class FirestoreConfig {

    /**
     * Creates the Firestore client, pointing to the emulator when an emulator host is configured.
     *
     * <p>The client retry budget is capped at {@link FirestoreDriverAdapter#MAX_WAIT}, so the SDK stops
     * retrying in the background once a request has given up (the SDK default is up to 60 s).
     *
     * @param projectId Firebase project id
     * @param emulatorHost emulator host:port, empty in production
     * @return Firestore client
     */
    @Bean
    public Firestore firestore(@Value("${app.firebase.project-id}") String projectId,
            @Value("${app.firebase.emulator-host:}") String emulatorHost) {
        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setRetrySettings(retrySettings());
        if (!emulatorHost.isBlank()) {
            builder.setEmulatorHost(emulatorHost);
        }
        return builder.build().getService();
    }

    /**
     * Retry settings bounded by {@link FirestoreDriverAdapter#MAX_WAIT}.
     *
     * @return client retry settings
     */
    static RetrySettings retrySettings() {
        Duration maxWait = FirestoreDriverAdapter.MAX_WAIT;
        return RetrySettings.newBuilder()
                .setInitialRetryDelayDuration(Duration.ofMillis(100))
                .setRetryDelayMultiplier(1.3)
                .setMaxRetryDelayDuration(Duration.ofMillis(500))
                .setInitialRpcTimeoutDuration(maxWait)
                .setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeoutDuration(maxWait)
                .setTotalTimeoutDuration(maxWait)
                .build();
    }
}
