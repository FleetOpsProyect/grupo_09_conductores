package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.out.EventMetricsPort;
import co.edu.univalle.conductores.application.port.out.EventPublisherPort;
import co.edu.univalle.conductores.application.port.out.OutboxRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            try {
                eventPublisher.publish(event.envelope());
            } catch (RuntimeException ex) {
                eventMetrics.recordPublicationFailure(event.envelope().eventType());
                outboxRepository.recordPublishFailure(event.envelope().eventId());
                LOGGER.warn("SNS publication failed for event {} ({})",
                        event.envelope().eventId(), event.envelope().eventType(), ex);
                continue;
            }
            outboxRepository.markAsSent(event.envelope().eventId());
            eventMetrics.recordPublished(event.envelope().eventType());
        }
        eventMetrics.setPendingEvents(outboxRepository.countPending());
    }
}
