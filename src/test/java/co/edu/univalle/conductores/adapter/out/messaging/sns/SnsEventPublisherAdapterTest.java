package co.edu.univalle.conductores.adapter.out.messaging.sns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.event.EventEnvelope;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SnsEventPublisherAdapterTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:fleetops_driver_events";

    @Mock
    private SnsClient snsClient;

    @Mock
    private JsonMapper jsonMapper;

    private SnsEventPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SnsEventPublisherAdapter(snsClient, jsonMapper, TOPIC_ARN);
    }

    @Test
    void publishesEnvelopeWithEventTypeMessageAttribute() {
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(
                UUID.randomUUID(),
                "driver_activated_event",
                OffsetDateTime.parse("2026-11-14T10:00:00-05:00"),
                "driver",
                UUID.randomUUID(),
                Map.of("driver_id", UUID.randomUUID().toString(), "full_name", "Carlos Pérez"));
        String message = "{\"event_type\":\"driver_activated_event\"}";
        when(jsonMapper.writeValueAsString(event)).thenReturn(message);

        adapter.publish(event);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        PublishRequest request = requestCaptor.getValue();
        assertThat(request.topicArn()).isEqualTo(TOPIC_ARN);
        assertThat(request.message()).isEqualTo(message);
        assertThat(request.messageAttributes()).containsOnlyKeys("event_type");
        assertThat(request.messageAttributes().get("event_type").dataType()).isEqualTo("String");
        assertThat(request.messageAttributes().get("event_type").stringValue())
                .isEqualTo("driver_activated_event");
    }
}
