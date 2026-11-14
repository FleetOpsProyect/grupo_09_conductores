package co.edu.univalle.conductores.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.Command;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.DlqSenderPort;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DriverEventListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID DRIVER_ID = UUID.fromString("96b65376-7878-4390-bfba-cbdfa67b57c6");

    @Mock
    private ProcessDriverEvent processDriverEvent;

    @Mock
    private ConsumerMetricsPort consumerMetrics;

    @Mock
    private DlqSenderPort dlqSender;

    private DriverEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DriverEventListener(processDriverEvent, consumerMetrics, dlqSender, new JsonMapper());
    }

    @Test
    void processesRawDocumentExpiredEvent() {
        String payload = """
                {
                  "event_id": "%s",
                  "event_type": "document_expired_event",
                  "occurred_at": "2026-11-14T12:00:00-05:00",
                  "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                  "data": {
                    "driver_id": "%s"
                  }
                }
                """.formatted(EVENT_ID, DRIVER_ID);

        listener.onMessage(payload, "1");

        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(processDriverEvent).process(captor.capture());
        Command command = captor.getValue();
        assertThat(command.eventId()).isEqualTo(EVENT_ID);
        assertThat(command.driverId()).isEqualTo(DRIVER_ID);
        assertThat(command.eventType()).isEqualTo("document_expired_event");
        verify(dlqSender, never()).sendToDlq(any(), any(), any());
    }

    @Test
    void processesSnsWrappedNotification() {
        String innerJson = "{\\\"event_id\\\":\\\"" + EVENT_ID
                + "\\\",\\\"event_type\\\":\\\"document_renewed_event\\\",\\\"data\\\":{\\\"driver_id\\\":\\\""
                + DRIVER_ID + "\\\"}}";

        String payload = """
                {
                  "Type": "Notification",
                  "MessageId": "sns-123",
                  "Message": "%s"
                }
                """.formatted(innerJson);

        listener.onMessage(payload, "1");

        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(processDriverEvent).process(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("document_renewed_event");
    }

    @Test
    void sendsInvalidJsonToDlq() {
        String badPayload = "this is not json";

        listener.onMessage(badPayload, "1");

        verify(consumerMetrics).recordDlq("unknown", "invalid_message");
        verify(dlqSender).sendToDlq(badPayload, "unknown", "invalid_message");
        verify(processDriverEvent, never()).process(any());
    }

    @Test
    void sendsMissingFieldsToDlq() {
        String incompletePayload = """
                {
                  "event_id": "%s"
                }
                """.formatted(EVENT_ID);

        listener.onMessage(incompletePayload, "1");

        verify(consumerMetrics).recordDlq("unknown", "invalid_message");
        verify(dlqSender).sendToDlq(incompletePayload, "unknown", "invalid_message");
        verify(processDriverEvent, never()).process(any());
    }

    @Test
    void sendsMalformedUuidToDlq() {
        String badUuidPayload = """
                {
                  "event_id": "not-a-valid-uuid",
                  "event_type": "document_expired_event",
                  "data": {
                    "driver_id": "%s"
                  }
                }
                """.formatted(DRIVER_ID);

        listener.onMessage(badUuidPayload, "1");

        verify(consumerMetrics).recordDlq("document_expired_event", "invalid_message");
        verify(dlqSender).sendToDlq(badUuidPayload, "document_expired_event", "invalid_message");
        verify(processDriverEvent, never()).process(any());
    }

    @Test
    void sendsToDlqWhenDriverNotFound() {
        String payload = """
                {
                  "event_id": "%s",
                  "event_type": "severe_incident_reported_event",
                  "data": {
                    "driver_id": "%s"
                  }
                }
                """.formatted(EVENT_ID, DRIVER_ID);

        doThrow(new DriverNotFoundException(DRIVER_ID))
                .when(processDriverEvent).process(any());

        listener.onMessage(payload, "1");

        verify(consumerMetrics).recordDlq("severe_incident_reported_event", "driver_not_found");
        verify(dlqSender).sendToDlq(payload, "severe_incident_reported_event", "driver_not_found");
    }

    @Test
    void rethrowsTransientFailureWithoutDlqWhenUnderMaxRetries() {
        String payload = """
                {
                  "event_id": "%s",
                  "event_type": "document_expired_event",
                  "data": {
                    "driver_id": "%s"
                  }
                }
                """.formatted(EVENT_ID, DRIVER_ID);

        doThrow(new IllegalStateException("Firestore timeout"))
                .when(processDriverEvent).process(any());

        assertThatThrownBy(() -> listener.onMessage(payload, "1"))
                .isInstanceOf(IllegalStateException.class);

        verify(consumerMetrics, never()).recordDlq(any(), any());
        verify(dlqSender, never()).sendToDlq(any(), any(), any());
    }

    @Test
    void recordsMaxRetriesMetricWhenTransientFailureReachesMaxReceives() {
        String payload = """
                {
                  "event_id": "%s",
                  "event_type": "document_expired_event",
                  "data": {
                    "driver_id": "%s"
                  }
                }
                """.formatted(EVENT_ID, DRIVER_ID);

        doThrow(new IllegalStateException("Firestore timeout"))
                .when(processDriverEvent).process(any());

        assertThatThrownBy(() -> listener.onMessage(payload, "3"))
                .isInstanceOf(IllegalStateException.class);

        verify(consumerMetrics).recordDlq("document_expired_event", "max_retries");
    }
}

