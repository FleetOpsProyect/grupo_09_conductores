package co.edu.univalle.conductores.application.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.out.EventMetricsPort;
import co.edu.univalle.conductores.application.port.out.EventPublisherPort;
import co.edu.univalle.conductores.application.port.out.OutboxRepositoryPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final int BATCH_SIZE = 10;

    @Mock
    private OutboxRepositoryPort outboxRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private EventMetricsPort eventMetrics;

    private OutboxPublisher outboxPublisher;
    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxRepository, eventPublisher, eventMetrics);
        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                "driver_activated_event",
                OffsetDateTime.parse("2026-11-14T10:00:00-05:00"),
                "driver",
                UUID.randomUUID(),
                Map.of("driver_id", UUID.randomUUID().toString(), "full_name", "Carlos Pérez"));
        event = OutboxEvent.pending(envelope);
    }

    @Test
    void marksEventSentOnlyAfterSuccessfulPublication() {
        when(outboxRepository.findPending(BATCH_SIZE)).thenReturn(List.of(event));
        when(outboxRepository.countPending()).thenReturn(1L, 0L);

        outboxPublisher.publishPending(BATCH_SIZE);

        InOrder order = inOrder(eventPublisher, outboxRepository);
        order.verify(eventPublisher).publish(event.envelope());
        order.verify(outboxRepository).markAsSent(event.envelope().eventId());
        verify(eventMetrics).recordPublished("driver_activated_event");
        verify(eventMetrics).setPendingEvents(0L);
    }

    @Test
    void recordsFailureAndLeavesEventPendingWhenPublicationFails() {
        when(outboxRepository.findPending(BATCH_SIZE)).thenReturn(List.of(event));
        when(outboxRepository.countPending()).thenReturn(1L, 1L);
        doThrow(new IllegalStateException("SNS unavailable"))
                .when(eventPublisher)
                .publish(event.envelope());

        outboxPublisher.publishPending(BATCH_SIZE);

        verify(outboxRepository).recordPublishFailure(event.envelope().eventId());
        verify(outboxRepository, never()).markAsSent(event.envelope().eventId());
        verify(eventMetrics).recordPublicationFailure("driver_activated_event");
        verify(eventMetrics, times(2)).setPendingEvents(1L);
    }
}
