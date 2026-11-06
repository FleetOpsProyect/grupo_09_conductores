package co.edu.univalle.conductores.config;

import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Firestore client configuration.
 */
@Configuration
@EnableConfigurationProperties(FirestoreProperties.class)
public class FirestoreConfig {

    /**
     * Creates the Firestore client, pointing to the emulator when an emulator host is configured.
     *
     * <p>The client retry budget is capped at the configured timeout ({@code app.firestore.timeout}),
     * so the SDK stops retrying in the background once a request has given up (the SDK default is up
     * to 60 s).
     *
     * @param projectId Firebase project id
     * @param emulatorHost emulator host:port, empty in production
     * @param properties Firestore settings
     * @return Firestore client
     */
    @Bean
    public Firestore firestore(@Value("${app.firebase.project-id}") String projectId,
            @Value("${app.firebase.emulator-host:}") String emulatorHost,
            FirestoreProperties properties) {
        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setRetrySettings(retrySettings(properties.timeout()));
        if (!emulatorHost.isBlank()) {
            builder.setEmulatorHost(emulatorHost);
        }
        return builder.build().getService();
    }

    /**
     * Retry settings bounded by the given timeout.
     *
     * @param timeout total and per-RPC time budget
     * @return client retry settings
     */
    static RetrySettings retrySettings(Duration timeout) {
        return RetrySettings.newBuilder()
                .setInitialRetryDelayDuration(Duration.ofMillis(100))
                .setRetryDelayMultiplier(1.3)
                .setMaxRetryDelayDuration(Duration.ofMillis(500))
                .setInitialRpcTimeoutDuration(timeout)
                .setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeoutDuration(timeout)
                .setTotalTimeoutDuration(timeout)
                .build();
    }
}
