package co.edu.univalle.conductores.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.application.port.out.ProcessedEventRepositoryPort;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import com.google.cloud.firestore.Firestore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TEC_06 y HU-04 contra LocalStack real: el listener consume de la cola, que tiene redrive policy hacia la
 * DLQ con {@code maxReceiveCount = 3}, y escribe en el emulador de Firestore. TEC_06: mensaje inválido o de
 * tipo desconocido, {@code event_id} repetido y Firestore caído. HU-04 (c8), con el payload de
 * Documentación: documento de un vehículo (se confirma sin cambios), conductor ACTIVE (se bloquea y se
 * guarda driver_blocked_event), conductor ya bloqueado (sin evento) y conductor inexistente (DLQ).
 *
 * <p>El fallo de Firestore se simula con un spy sobre el puerto de persistencia, solo para un
 * {@code event_id}, porque el emulador lo comparten todas las pruebas de integración.
 */
@DirtiesContext
class DriverEventListenerLocalStackIntegrationTest extends BaseIntegrationTest {

    private static final int LOCALSTACK_PORT = 4566;
    private static final String REGION = "us-east-1";
    private static final String QUEUE = "fleetops_driver_queue_it";
    private static final String DLQ = "fleetops_driver_dlq_it";
    private static final String EXPIRED = "document_expired_event";
    private static final String RENEWED = "document_renewed_event";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @SuppressWarnings("resource")
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
            DockerImageName.parse("localstack/localstack:4.14.0"))
            .withEnv("SERVICES", "sqs")
            .withEnv("AWS_DEFAULT_REGION", REGION)
            .withExposedPorts(LOCALSTACK_PORT)
            .waitingFor(Wait.forHttp("/_localstack/health").forPort(LOCALSTACK_PORT));

    private static SqsClient sqs;
    private static String queueUrl;
    private static String dlqUrl;

    /** DLQ messages already received, so a test can find its own message among the others. */
    private static final List<Message> DLQ_MESSAGES = new ArrayList<>();

    private final JsonMapper jsonMapper = new JsonMapper();

    @Autowired
    private RegisterDriver registerDriver;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Firestore firestore;

    @MockitoSpyBean
    private ProcessedEventRepositoryPort processedEvents;

    @DynamicPropertySource
    static void messagingProperties(DynamicPropertyRegistry registry) {
        LOCALSTACK.start();
        String endpoint = "http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(LOCALSTACK_PORT);
        sqs = SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
        dlqUrl = sqs.createQueue(b -> b.queueName(DLQ)).queueUrl();
        String dlqArn = sqs.getQueueAttributes(b -> b.queueUrl(dlqUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
        queueUrl = sqs.createQueue(b -> b.queueName(QUEUE).attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}"))).queueUrl();

        registry.add("spring.cloud.aws.endpoint", () -> endpoint);
        registry.add("spring.cloud.aws.region.static", () -> REGION);
        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        registry.add("app.messaging.consumer-enabled", () -> "true");
        registry.add("app.messaging.driver-queue", () -> queueUrl);
        registry.add("app.messaging.driver-dlq-url", () -> dlqUrl);
    }

    @Test
    void activeDriverIsBlockedAndDriverBlockedEventIsStoredWithTheReceivedCorrelationId() throws Exception {
        UUID driverId = newDriver();
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        send(expiredEnvelope(eventId, correlationId, "DRIVER", driverId.toString()));

        await().atMost(TIMEOUT).until(() -> statusOf(driverId) == DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        await().atMost(TIMEOUT).until(() -> messagesLeftInQueue() == 0);
        assertThat(dlqMessageContaining(eventId.toString())).isEmpty();
        List<JsonNode> blocked = blockedEventsOf(driverId);
        assertThat(blocked).singleElement().satisfies(event -> {
            assertThat(event.get("correlation_id").asString()).isEqualTo(correlationId.toString());
            assertThat(event.get("source").asString()).isEqualTo("driver");
            assertThat(event.get("data").get("driver_id").asString()).isEqualTo(driverId.toString());
        });
    }

    @Test
    void documentOfAVehicleIsAcknowledgedWithoutChanges() {
        UUID eventId = UUID.randomUUID();
        double ignoredBefore = consumed(EXPIRED, "ignored");

        send(expiredEnvelope(eventId, UUID.randomUUID(), "VEHICLE", "01960000-0000-7000-8000-000000000001"));

        await().atMost(TIMEOUT).until(() -> consumed(EXPIRED, "ignored") == ignoredBefore + 1);
        await().atMost(TIMEOUT).until(() -> messagesLeftInQueue() == 0);
        verify(processedEvents, never()).applyOnce(eq(eventId), any(), any(), any(), any());
        assertThat(dlqMessageContaining(eventId.toString())).isEmpty();
    }

    @Test
    void alreadyBlockedDriverStoresNoSecondDriverBlockedEvent() throws Exception {
        UUID driverId = newDriver();
        send(expiredEnvelope(UUID.randomUUID(), UUID.randomUUID(), "DRIVER", driverId.toString()));
        await().atMost(TIMEOUT).until(() -> blockedEventsOf(driverId).size() == 1);
        double processedBefore = consumed(EXPIRED, "processed");

        send(expiredEnvelope(UUID.randomUUID(), UUID.randomUUID(), "DRIVER", driverId.toString()));

        await().atMost(TIMEOUT).until(() -> consumed(EXPIRED, "processed") == processedBefore + 1);
        await().atMost(TIMEOUT).until(() -> messagesLeftInQueue() == 0);
        assertThat(statusOf(driverId)).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(blockedEventsOf(driverId)).hasSize(1);
    }

    @Test
    void unknownDriverGoesToDlqWithoutRetries() {
        UUID eventId = UUID.randomUUID();

        send(expiredEnvelope(eventId, UUID.randomUUID(), "DRIVER", UUID.randomUUID().toString()));

        Message moved = awaitDlqMessageContaining(eventId.toString());
        assertThat(moved.messageAttributes().get("reason").stringValue()).isEqualTo("driver_not_found");
        assertThat(moved.messageAttributes().get("event_type").stringValue()).isEqualTo(EXPIRED);
        verify(processedEvents, times(1)).applyOnce(eq(eventId), any(), any(), any(), any());
        await().atMost(TIMEOUT).until(() -> messagesLeftInQueue() == 0);
    }

    @Test
    void invalidAndUnknownTypeMessagesGoToDlqWithoutRetries() {
        String invalidJson = "not json " + UUID.randomUUID();
        UUID unknownTypeEventId = UUID.randomUUID();
        String unknownType = envelope(unknownTypeEventId, "driver_teleported_event", UUID.randomUUID());

        send(invalidJson);
        send(unknownType);

        Message invalid = awaitDlqMessageContaining(invalidJson);
        Message unknown = awaitDlqMessageContaining(unknownTypeEventId.toString());
        assertThat(invalid.messageAttributes().get("reason").stringValue()).isEqualTo("invalid_message");
        assertThat(unknown.messageAttributes().get("reason").stringValue()).isEqualTo("invalid_message");
        assertThat(unknown.messageAttributes().get("event_type").stringValue()).isEqualTo("driver_teleported_event");
        verify(processedEvents, never()).applyOnce(eq(unknownTypeEventId), any(), any(), any(), any());
        await().atMost(TIMEOUT).until(() -> messagesLeftInQueue() == 0);
    }

    @Test
    void repeatedEventIdIsAcknowledgedWithoutChanges() {
        UUID driverId = newDriver();
        UUID eventId = UUID.randomUUID();
        send(envelope(eventId, EXPIRED, driverId));
        await().atMost(TIMEOUT).until(() -> statusOf(driverId) == DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        double duplicatesBefore = consumed(RENEWED, "duplicate");

        send(envelope(eventId, RENEWED, driverId));

        await().atMost(TIMEOUT).until(() -> consumed(RENEWED, "duplicate") == duplicatesBefore + 1);
        await().atMost(TIMEOUT).until(() -> messagesLeftInQueue() == 0);
        assertThat(statusOf(driverId)).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(dlqMessageContaining(eventId.toString())).isEmpty();
    }

    @Test
    void firestoreFailureIsRetriedAndMovesToDlqOnTheThirdAttempt() {
        UUID driverId = newDriver();
        UUID eventId = UUID.randomUUID();
        doThrow(new PersistenceUnavailableException("Firestore unavailable (simulated)", null))
                .when(processedEvents).applyOnce(eq(eventId), any(), any(), any(), any());
        double maxRetriesBefore = dlqCount(EXPIRED, "max_retries");

        send(envelope(eventId, EXPIRED, driverId));

        Message moved = awaitDlqMessageContaining(eventId.toString());
        assertThat(moved.messageAttributes()).doesNotContainKey("reason");
        verify(processedEvents, times(3)).applyOnce(eq(eventId), any(), any(), any(), any());
        assertThat(dlqCount(EXPIRED, "max_retries")).isEqualTo(maxRetriesBefore + 1);
        assertThat(statusOf(driverId)).isEqualTo(DriverStatus.ACTIVE);
    }

    private UUID newDriver() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        return registerDriver.register(new RegisterDriver.Command(
                "Conductor Prueba", document, "it" + document + "@correo.com", null, UUID.randomUUID())).getId();
    }

    /** A document_expired_event as Documentación publishes it, for an owner of the given type. */
    private String expiredEnvelope(UUID eventId, UUID correlationId, String ownerType, String ownerId) {
        ObjectNode envelope = envelopeWithoutData(eventId, EXPIRED, correlationId);
        envelope.putObject("data")
                .put("document_id", UUID.randomUUID().toString())
                .put("document_type", "DRIVER".equals(ownerType) ? "DRIVER_LICENSE" : "SOAT")
                .put("owner_type", ownerType)
                .put("owner_id", ownerId)
                .put("expiration_date", "2026-11-10");
        return envelope.toString();
    }

    private String renewedEnvelope(UUID eventId, UUID correlationId, String ownerId) {
        ObjectNode envelope = envelopeWithoutData(eventId, RENEWED, correlationId);
        envelope.putObject("data")
                .put("document_id", UUID.randomUUID().toString())
                .put("previous_document_id", UUID.randomUUID().toString())
                .put("origin", "RENEWAL")
                .put("document_type", "DRIVER_LICENSE")
                .put("owner_type", "DRIVER")
                .put("owner_id", ownerId);
        return envelope.toString();
    }

    private String envelope(UUID eventId, String eventType, UUID driverId) {
        if (EXPIRED.equals(eventType)) {
            return expiredEnvelope(eventId, UUID.randomUUID(), "DRIVER", driverId.toString());
        }
        if (RENEWED.equals(eventType)) {
            return renewedEnvelope(eventId, UUID.randomUUID(), driverId.toString());
        }
        ObjectNode envelope = envelopeWithoutData(eventId, eventType, UUID.randomUUID());
        envelope.putObject("data").put("driver_id", driverId.toString());
        return envelope.toString();
    }

    private ObjectNode envelopeWithoutData(UUID eventId, String eventType, UUID correlationId) {
        ObjectNode envelope = jsonMapper.createObjectNode();
        envelope.put("event_id", eventId.toString());
        envelope.put("event_type", eventType);
        envelope.put("occurred_at", "2026-11-15T10:00:00-05:00");
        envelope.put("source", "documents");
        envelope.put("correlation_id", correlationId.toString());
        return envelope;
    }

    /** driver_blocked_event envelopes stored in outbox_events for a driver. */
    private List<JsonNode> blockedEventsOf(UUID driverId) throws Exception {
        return firestore.collection("outbox_events")
                .whereEqualTo("event_type", "driver_blocked_event")
                .get().get().getDocuments().stream()
                .map(document -> jsonMapper.readTree(document.getString("payload")))
                .filter(payload -> driverId.toString().equals(payload.get("data").get("driver_id").asString()))
                .toList();
    }

    private static void send(String body) {
        sqs.sendMessage(b -> b.queueUrl(queueUrl).messageBody(body));
    }

    private DriverStatus statusOf(UUID driverId) {
        return driverRepository.findById(driverId).orElseThrow().getStatus();
    }

    private static int messagesLeftInQueue() {
        Map<QueueAttributeName, String> attributes = sqs.getQueueAttributes(b -> b.queueUrl(queueUrl).attributeNames(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).attributes();
        return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    private static Message awaitDlqMessageContaining(String marker) {
        return await().atMost(TIMEOUT).until(() -> dlqMessageContaining(marker), Optional::isPresent).orElseThrow();
    }

    private static synchronized Optional<Message> dlqMessageContaining(String marker) {
        DLQ_MESSAGES.addAll(sqs.receiveMessage(b -> b.queueUrl(dlqUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .messageAttributeNames("All")).messages());
        return DLQ_MESSAGES.stream().filter(message -> message.body().contains(marker)).findFirst();
    }

    private double consumed(String eventType, String result) {
        Counter counter = meterRegistry.find("drivers_events_consumed")
                .tags("event_type", eventType, "result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private double dlqCount(String eventType, String reason) {
        Counter counter = meterRegistry.find("drivers_events_dlq")
                .tags("event_type", eventType, "reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }
}
