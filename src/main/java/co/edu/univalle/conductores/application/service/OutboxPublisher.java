package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.logging.LogContext;
import co.edu.univalle.conductores.application.port.out.EventMetricsPort;
import co.edu.univalle.conductores.application.port.out.EventPublisherPort;
import co.edu.univalle.conductores.application.port.out.OutboxRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Publica los eventos pendientes del outbox y actualiza su estado de entrega.
 */
public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepositoryPort outboxRepository;
    private final EventPublisherPort eventPublisher;
    private final EventMetricsPort eventMetrics;

    /**
     * Crea el publicador del outbox.
     *
     * @param outboxRepository puerto para consultar y actualizar eventos pendientes
     * @param eventPublisher puerto para enviar eventos al broker
     * @param eventMetrics métricas de publicación del outbox
     */
    public OutboxPublisher(
            OutboxRepositoryPort outboxRepository,
            EventPublisherPort eventPublisher,
            EventMetricsPort eventMetrics) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.eventMetrics = eventMetrics;
    }

    /**
     * Publica un lote de eventos pendientes.
     *
     * @param limit máximo de eventos a procesar en este lote
     */
    public void publishPending(int limit) {
        eventMetrics.setPendingEvents(outboxRepository.countPending());
        for (OutboxEvent event : outboxRepository.findPending(limit)) {
            EventEnvelope envelope = event.envelope();
            MDC.put(LogContext.EVENT_ID, String.valueOf(envelope.eventId()));
            MDC.put(LogContext.EVENT_TYPE, envelope.eventType());
            MDC.put(LogContext.CORRELATION_ID, String.valueOf(envelope.correlationId()));
            try {
                publish(envelope);
            } finally {
                MDC.remove(LogContext.EVENT_ID);
                MDC.remove(LogContext.EVENT_TYPE);
                MDC.remove(LogContext.CORRELATION_ID);
            }
        }
        eventMetrics.setPendingEvents(outboxRepository.countPending());
    }

    private void publish(EventEnvelope envelope) {
        try {
            eventPublisher.publish(envelope);
        } catch (RuntimeException ex) {
            eventMetrics.recordPublicationFailure(envelope.eventType());
            outboxRepository.recordPublishFailure(envelope.eventId());
            LOGGER.warn("SNS publication failed for event {} ({})", envelope.eventId(), envelope.eventType(), ex);
            return;
        }
        outboxRepository.markAsSent(envelope.eventId());
        eventMetrics.recordPublished(envelope.eventType());
    }
}
