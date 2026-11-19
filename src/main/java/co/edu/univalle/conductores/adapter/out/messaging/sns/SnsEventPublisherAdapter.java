package co.edu.univalle.conductores.adapter.out.messaging.sns;

import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.port.out.EventPublisherPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapter de salida que publica eventos de Conductores en SNS.
 */
@Component
public class SnsEventPublisherAdapter implements EventPublisherPort {

    private final SnsClient snsClient;
    private final JsonMapper jsonMapper;
    private final String topicArn;

    /**
     * Crea el adapter SNS.
     *
     * @param snsClient cliente AWS SNS
     * @param jsonMapper mapper configurado por Spring
     * @param topicArn ARN del topic de eventos de Conductores
     */
    public SnsEventPublisherAdapter(
            SnsClient snsClient,
            JsonMapper jsonMapper,
            @Value("${app.messaging.driver-events-topic-arn}") String topicArn) {
        this.snsClient = snsClient;
        this.jsonMapper = jsonMapper;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(EventEnvelope<?> event) {
        String message;
        try {
            message = jsonMapper.writeValueAsString(event);
        } catch (JacksonException ex) {
            throw new IllegalStateException("No se pudo serializar el evento para SNS", ex);
        }

        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(message)
                .messageAttributes(Map.of(
                        "event_type",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(event.eventType())
                                .build()))
                .build();

        snsClient.publish(request);
    }
}
