package co.edu.univalle.conductores.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.Command;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.DlqSenderPort;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class DriverEventListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID DRIVER_ID = UUID.fromString("96b65376-7878-4390-bfba-cbdfa67b57c6");
    private static final UUID CORRELATION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String OCCURRED_AT = "2026-11-14T12:00:00-05:00";
    private static final String EXPIRED = "document_expired_event";

    private final JsonMapper jsonMapper = new JsonMapper();

    @Mock
    private ProcessDriverEvent processDriverEvent;

    @Mock
    private ConsumerMetricsPort consumerMetrics;

    @Mock
    private DlqSenderPort dlqSender;

    private DriverEventListener listener;
    private ListAppender<ILoggingEvent> logs;
    private Logger listenerLogger;

    @BeforeEach
    void setUp() {
        listener = new DriverEventListener(processDriverEvent, consumerMetrics, dlqSender, jsonMapper);
        listenerLogger = (Logger) LoggerFactory.getLogger(DriverEventListener.class);
        logs = new ListAppender<>();
        logs.start();
        listenerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        listenerLogger.detachAppender(logs);
    }

    @Test
    void readsTheReceiveCountHeaderThatSpringCloudAwsPublishes() throws NoSuchMethodException {
        Method onMessage = DriverEventListener.class.getMethod("onMessage", String.class, String.class);
        Header header = onMessage.getParameters()[1].getAnnotation(Header.class);

        assertThat(header.name())
                .isEqualTo(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT)
                .isEqualTo("Sqs_Msa_ApproximateReceiveCount");
    }

    @Test
    void processesValidEnvelope() {
        listener.onMessage(validEnvelope().toString(), "1");

        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(processDriverEvent).process(captor.capture());
        Command command = captor.getValue();
        assertThat(command.eventId()).isEqualTo(EVENT_ID);
        assertThat(command.eventType()).isEqualTo(EXPIRED);
        assertThat(command.driverId()).isEqualTo(DRIVER_ID);
        assertThat(command.occurredAt()).isEqualTo(OffsetDateTime.parse(OCCURRED_AT));
        assertThat(command.correlationId()).isEqualTo(CORRELATION_ID);
        verify(dlqSender, never()).sendToDlq(any(), any(), any());
    }

    @Test
    void ignoresExtraFields() {
        ObjectNode envelope = validEnvelope();
        envelope.put("extra", "ignored");
        ((ObjectNode) envelope.get("data")).put("document_id", "doc-1");

        listener.onMessage(envelope.toString(), "1");

        verify(processDriverEvent).process(any());
    }

    @Test
    void processesSnsWrappedNotification() {
        ObjectNode notification = jsonMapper.createObjectNode();
        notification.put("Type", "Notification");
        notification.put("MessageId", "sns-123");
        notification.put("Message", validEnvelope().put("event_type", "document_renewed_event").toString());

        listener.onMessage(notification.toString(), "1");

        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(processDriverEvent).process(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("document_renewed_event");
    }

    @Test
    void sendsInvalidJsonToDlqWithErrorLog() {
        String badPayload = "this is not json";

        listener.onMessage(badPayload, "1");

        verify(consumerMetrics).recordDlq("unknown", "invalid_message");
        verify(dlqSender).sendToDlq(badPayload, "unknown", "invalid_message");
        verify(processDriverEvent, never()).process(any());
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("JSON inválido");
        });
    }

    @Test
    void sendsSnsNotificationWithInvalidInnerJsonToDlq() {
        ObjectNode notification = jsonMapper.createObjectNode();
        notification.put("Type", "Notification");
        notification.put("Message", "{not json");

        listener.onMessage(notification.toString(), "1");

        verify(dlqSender).sendToDlq(notification.toString(), "unknown", "invalid_message");
        verify(processDriverEvent, never()).process(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"event_id", "event_type", "occurred_at", "source", "correlation_id", "data"})
    void sendsEnvelopeWithMissingFieldToDlqNamingTheField(String field) {
        ObjectNode envelope = validEnvelope();
        envelope.remove(field);

        listener.onMessage(envelope.toString(), "1");

        verify(consumerMetrics).recordDlq(any(), any());
        verify(dlqSender).sendToDlq(any(), any(), any());
        verify(processDriverEvent, never()).process(any());
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("Falta el campo obligatorio " + field);
        });
    }

    @Test
    void sendsEnvelopeWithoutDriverIdToDlqWithEventIdInTheLog() {
        ObjectNode envelope = validEnvelope();
        ((ObjectNode) envelope.get("data")).remove("driver_id");

        listener.onMessage(envelope.toString(), "1");

        verify(dlqSender).sendToDlq(envelope.toString(), EXPIRED, "invalid_message");
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains("event_id=" + EVENT_ID)
                    .contains("Falta el campo obligatorio driver_id");
        });
    }

    @Test
    void sendsBlankEventTypeToDlq() {
        String payload = validEnvelope().put("event_type", "  ").toString();

        listener.onMessage(payload, "1");

        verify(dlqSender).sendToDlq(payload, "unknown", "invalid_message");
    }

    @Test
    void sendsNonObjectDataToDlq() {
        String payload = validEnvelope().put("data", "not-an-object").toString();

        listener.onMessage(payload, "1");

        verify(dlqSender).sendToDlq(payload, EXPIRED, "invalid_message");
    }

    @Test
    void sendsMalformedUuidToDlq() {
        String payload = validEnvelope().put("event_id", "not-a-valid-uuid").toString();

        listener.onMessage(payload, "1");

        verify(consumerMetrics).recordDlq(EXPIRED, "invalid_message");
        verify(dlqSender).sendToDlq(payload, EXPIRED, "invalid_message");
        verify(processDriverEvent, never()).process(any());
    }

    @Test
    void sendsMalformedOccurredAtToDlq() {
        String payload = validEnvelope().put("occurred_at", "14/11/2026").toString();

        listener.onMessage(payload, "1");

        verify(dlqSender).sendToDlq(payload, EXPIRED, "invalid_message");
        verify(processDriverEvent, never()).process(any());
    }

    @Test
    void sendsUnsupportedEventTypeToDlqWithErrorLog() {
        String payload = validEnvelope().put("event_type", "driver_teleported_event").toString();
        doThrow(new IllegalArgumentException("Tipo de evento no soportado: driver_teleported_event"))
                .when(processDriverEvent).process(any());

        listener.onMessage(payload, "1");

        verify(consumerMetrics).recordDlq("driver_teleported_event", "invalid_message");
        verify(dlqSender).sendToDlq(payload, "driver_teleported_event", "invalid_message");
        assertThat(logs.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void sendsToDlqWithWarnLogWhenDriverNotFound() {
        String payload = validEnvelope().put("event_type", "severe_incident_reported_event").toString();
        doThrow(new DriverNotFoundException(DRIVER_ID)).when(processDriverEvent).process(any());

        listener.onMessage(payload, "1");

        verify(consumerMetrics).recordDlq("severe_incident_reported_event", "driver_not_found");
        verify(dlqSender).sendToDlq(payload, "severe_incident_reported_event", "driver_not_found");
        assertThat(logs.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1", "2", "not-a-number"})
    void rethrowsTransientFailureWithoutDlqBeforeTheLastAttempt(String receiveCount) {
        String payload = validEnvelope().toString();
        doThrow(new IllegalStateException("Firestore timeout")).when(processDriverEvent).process(any());

        assertThatThrownBy(() -> listener.onMessage(payload, receiveCount))
                .isInstanceOf(IllegalStateException.class);

        verify(consumerMetrics, never()).recordDlq(any(), any());
        verify(dlqSender, never()).sendToDlq(any(), any(), any());
    }

    @Test
    void recordsMaxRetriesOnTheLastAttemptAndStillRethrows() {
        String payload = validEnvelope().toString();
        doThrow(new IllegalStateException("Firestore timeout")).when(processDriverEvent).process(any());

        assertThatThrownBy(() -> listener.onMessage(payload, "3"))
                .isInstanceOf(IllegalStateException.class);

        verify(consumerMetrics).recordDlq(EXPIRED, "max_retries");
        verify(dlqSender, never()).sendToDlq(any(), any(), any());
        assertThat(logs.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    private ObjectNode validEnvelope() {
        ObjectNode envelope = jsonMapper.createObjectNode();
        envelope.put("event_id", EVENT_ID.toString());
        envelope.put("event_type", EXPIRED);
        envelope.put("occurred_at", OCCURRED_AT);
        envelope.put("source", "documents");
        envelope.put("correlation_id", CORRELATION_ID.toString());
        envelope.putObject("data").put("driver_id", DRIVER_ID.toString());
        return envelope;
    }
}
