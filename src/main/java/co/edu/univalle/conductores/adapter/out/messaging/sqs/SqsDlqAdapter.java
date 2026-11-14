package co.edu.univalle.conductores.adapter.out.messaging.sqs;

import co.edu.univalle.conductores.application.port.out.DlqSenderPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Adapter de salida que envía mensajes fallidos o inválidos a la DLQ.
 */
@Component
public class SqsDlqAdapter implements DlqSenderPort {

    private final SqsAsyncClient sqsAsyncClient;
    private final String dlqUrl;

    /**
     * Construye el adaptador DLQ.
     *
     * @param sqsAsyncClient cliente SQS asíncrono
     * @param dlqUrl URL de la DLQ configurada
     */
    public SqsDlqAdapter(
            SqsAsyncClient sqsAsyncClient,
            @Value("${app.messaging.driver-dlq-url:fleetops_driver_dlq}") String dlqUrl) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.dlqUrl = dlqUrl;
    }

    @Override
    public void sendToDlq(String rawPayload, String eventType, String reason) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(dlqUrl)
                .messageBody(rawPayload)
                .messageAttributes(Map.of(
                        "event_type", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(eventType != null ? eventType : "unknown")
                                .build(),
                        "reason", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(reason)
                                .build()))
                .build();

        sqsAsyncClient.sendMessage(request).join();
    }
}
