package co.edu.univalle.conductores;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Clase base para pruebas de integración: levanta el contexto completo de Spring,
 * RabbitMQ (desde {@link TestcontainersConfiguration}) y el emulador de Firestore.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    private static final int FIRESTORE_PORT = 8080;

    static final GenericContainer<?> FIRESTORE_EMULATOR = new GenericContainer<>(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:590.0.1-emulators"))
            .withCommand("gcloud", "beta", "emulators", "firestore", "start",
                    "--host-port=0.0.0.0:" + FIRESTORE_PORT)
            .withExposedPorts(FIRESTORE_PORT)
            .waitingFor(Wait.forListeningPort());

    static {
        FIRESTORE_EMULATOR.start();
    }

    @DynamicPropertySource
    static void firestoreProperties(DynamicPropertyRegistry registry) {
        registry.add("app.firebase.emulator-host",
                () -> FIRESTORE_EMULATOR.getHost() + ":" + FIRESTORE_EMULATOR.getMappedPort(FIRESTORE_PORT));
    }

}
