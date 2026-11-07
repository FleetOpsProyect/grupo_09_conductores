package co.edu.univalle.conductores;

import com.google.cloud.firestore.Firestore;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Clase base para pruebas de integración: levanta el contexto completo de Spring
 * y el emulador de Firestore.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
public abstract class BaseIntegrationTest {

    /** Port the Firestore emulator listens on inside the container. */
    protected static final int FIRESTORE_PORT = 8080;

    /** Maximum total time to wait for the first successful Firestore read. */
    private static final Duration WARM_UP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration WARM_UP_RETRY_DELAY = Duration.ofMillis(250);

    private static final long ATTEMPT_TIMEOUT_SECONDS = 5;

    /** Collection read by the warm-up; same as the adapter's, which is not visible from here. */
    private static final String DRIVERS_COLLECTION = "drivers";

    static final GenericContainer<?> FIRESTORE_EMULATOR = newFirestoreEmulator();

    static {
        FIRESTORE_EMULATOR.start();
    }

    /**
     * Builds a Firestore emulator container that is not started yet. The shared emulator and any
     * test class that needs its own emulator use this, so the image is defined in one place.
     *
     * @return a new, unstarted emulator container
     */
    protected static GenericContainer<?> newFirestoreEmulator() {
        return new GenericContainer<>(
                DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:590.0.1-emulators"))
                .withCommand("gcloud", "beta", "emulators", "firestore", "start",
                        "--host-port=0.0.0.0:" + FIRESTORE_PORT)
                .withExposedPorts(FIRESTORE_PORT)
                .waitingFor(Wait.forListeningPort());
    }

    @DynamicPropertySource
    static void firestoreProperties(DynamicPropertyRegistry registry) {
        registry.add("app.firebase.emulator-host",
                () -> FIRESTORE_EMULATOR.getHost() + ":" + FIRESTORE_EMULATOR.getMappedPort(FIRESTORE_PORT));
    }

    /**
     * Warms up the Firestore client of the running class's context before its first test, so no
     * test pays for the first connection inside the 2 s budget of the client.
     *
     * @param firestore Firestore client of the current context
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    @BeforeAll
    static void warmUpFirestore(@Autowired Firestore firestore) throws InterruptedException {
        awaitFirestoreReady(firestore);
    }

    /**
     * Retries a read of the drivers collection on the given client until it succeeds or
     * {@link #WARM_UP_TIMEOUT} passes. A single wait is not enough: the client gives up each call
     * after its 2 s retry budget, which can be shorter than opening the first channel.
     *
     * @param firestore Firestore client to warm up
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws IllegalStateException if no read succeeds within {@link #WARM_UP_TIMEOUT}
     */
    protected static void awaitFirestoreReady(Firestore firestore) throws InterruptedException {
        long deadline = System.nanoTime() + WARM_UP_TIMEOUT.toNanos();
        Exception lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                firestore.collection(DRIVERS_COLLECTION).limit(1).get().get(ATTEMPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return;
            } catch (ExecutionException | TimeoutException ex) {
                lastFailure = ex;
                Thread.sleep(WARM_UP_RETRY_DELAY);
            }
        }
        throw new IllegalStateException(
                "Firestore emulator not ready after " + WARM_UP_TIMEOUT.toSeconds() + " s", lastFailure);
    }

}
