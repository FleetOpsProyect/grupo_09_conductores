package co.edu.univalle.conductores.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.port.out.EventPublisherPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/**
 * TEC05-2: prueba de punta a punta del outbox con SNS caído. Con el publicador programado activo,
 * {@code POST /drivers} responde 201 aunque SNS no responda; el evento queda PENDING y suma
 * {@code attempts}, y cuando SNS vuelve se publica en una pasada siguiente y queda SENT.
 *
 * <p>SNS se simula con un {@link EventPublisherPort} que falla mientras está "caído". El contexto es
 * propio y se cierra al terminar ({@link DirtiesContext}), para que el publicador no siga activo en
 * las demás pruebas de integración, que comparten el emulador de Firestore.
 */
@DirtiesContext
@TestPropertySource(properties = {
    "app.outbox.publisher-enabled=true",
    "app.outbox.publish-interval-ms=200",
    // Lote grande: el emulador compartido puede tener eventos PENDING de otras pruebas.
    "app.outbox.batch-size=500"
})
class OutboxSnsOutageIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String OUTBOX_COLLECTION = "outbox_events";

    @LocalServerPort
    private int port;

    @Autowired
    private Firestore firestore;

    @Autowired
    private SwitchableEventPublisher sns;

    @Test
    void driverIsCreatedWhileSnsIsDownAndEventIsPublishedWhenSnsReturns() throws Exception {
        sns.goDown();

        ResponseEntity<String> response = register(uniqueDocument());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String driverId = MAPPER.readTree(response.getBody()).get("id").asText();
        String eventId = outboxEventIdOf(driverId);

        await().atMost(TIMEOUT).until(() -> attemptsOf(eventId) > 0);
        assertThat(outboxEvent(eventId).getString("status")).isEqualTo("PENDING");
        assertThat(sns.published()).doesNotContain(UUID.fromString(eventId));

        sns.comeBack();

        await().atMost(TIMEOUT).until(() -> "SENT".equals(outboxEvent(eventId).getString("status")));
        assertThat(sns.published()).contains(UUID.fromString(eventId));
    }

    /** Busca el evento del outbox que generó el registro del conductor. */
    private String outboxEventIdOf(String driverId) throws Exception {
        var events = firestore.collection(OUTBOX_COLLECTION).get().get().getDocuments().stream()
                .filter(event -> String.valueOf(event.getString("payload")).contains(driverId))
                .toList();
        assertThat(events).hasSize(1);
        return events.get(0).getId();
    }

    private long attemptsOf(String eventId) throws Exception {
        Long attempts = outboxEvent(eventId).getLong("attempts");
        return attempts == null ? 0 : attempts;
    }

    private DocumentSnapshot outboxEvent(String eventId) throws Exception {
        return firestore.collection(OUTBOX_COLLECTION).document(eventId).get().get();
    }

    private ResponseEntity<String> register(String documentNumber) {
        String body = """
                {"full_name":"Carlos Pérez","document_number":"%s","email":"carlos@correo.com","phone":"3001234567"}
                """.formatted(documentNumber);
        return RestClient.builder()
                .baseUrl("http://localhost:" + port + "/drivers")
                .defaultStatusHandler(status -> true, (request, httpResponse) -> { })
                .build()
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private static String uniqueDocument() {
        return String.format("%010d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SnsSimulationConfig {

        @Bean
        @Primary
        SwitchableEventPublisher switchableEventPublisher() {
            return new SwitchableEventPublisher();
        }
    }

    /**
     * SNS simulado: falla mientras está caído y guarda los {@code event_id} publicados cuando está
     * disponible. El publicador programado lo llama desde otro hilo, por eso usa tipos concurrentes.
     */
    static final class SwitchableEventPublisher implements EventPublisherPort {

        private final AtomicBoolean available = new AtomicBoolean(true);
        private final Set<UUID> published = ConcurrentHashMap.newKeySet();

        @Override
        public void publish(EventEnvelope<?> event) {
            if (!available.get()) {
                throw new IllegalStateException("SNS simulado caído");
            }
            published.add(event.eventId());
        }

        void goDown() {
            available.set(false);
        }

        void comeBack() {
            available.set(true);
        }

        Set<UUID> published() {
            return Set.copyOf(published);
        }
    }
}
