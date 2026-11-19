package co.edu.univalle.conductores.adapter.out.messaging.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@ExtendWith(MockitoExtension.class)
class SqsDlqAdapterTest {

    private static final String DLQ_URL = "https://sqs.us-east-1.amazonaws.com/000000000000/fleetops_driver_dlq";

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private SqsDlqAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SqsDlqAdapter(sqsAsyncClient, DLQ_URL);
    }

    @Test
    void sendsMessageToDlqWithMessageAttributes() {
        SendMessageResponse response = SendMessageResponse.builder().messageId("msg-123").build();
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String rawPayload = "{\"bad\":\"message\"}";
        adapter.sendToDlq(rawPayload, "document_expired_event", "invalid_message");

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsAsyncClient).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();

        assertThat(request.queueUrl()).isEqualTo(DLQ_URL);
        assertThat(request.messageBody()).isEqualTo(rawPayload);
        assertThat(request.messageAttributes().get("event_type").stringValue()).isEqualTo("document_expired_event");
        assertThat(request.messageAttributes().get("reason").stringValue()).isEqualTo("invalid_message");
    }

    @Test
    void sendsMessageToDlqWithNullEventTypeDefaultingToUnknown() {
        SendMessageResponse response = SendMessageResponse.builder().messageId("msg-123").build();
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String rawPayload = "corrupted";
        adapter.sendToDlq(rawPayload, null, "invalid_message");

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsAsyncClient).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();

        assertThat(request.messageAttributes().get("event_type").stringValue()).isEqualTo("unknown");
    }
}

