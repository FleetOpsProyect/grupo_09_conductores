package co.edu.univalle.conductores.adapter.in.scheduling;

import co.edu.univalle.conductores.application.service.OutboxPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparador periódico del publicador del outbox.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.outbox", name = "publisher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

    private final OutboxPublisher outboxPublisher;
    private final int batchSize;

    /**
     * Crea el disparador del outbox.
     *
     * @param outboxPublisher servicio que publica los eventos pendientes
     * @param batchSize tamaño máximo de cada lote
     */
    public OutboxPublisherScheduler(
            OutboxPublisher outboxPublisher,
            @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.outboxPublisher = outboxPublisher;
        this.batchSize = batchSize;
    }

    /**
     * Publica los eventos pendientes de acuerdo al intervalo configurado.
     */
    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:2000}")
    public void publishPendingEvents() {
        outboxPublisher.publishPending(batchSize);
    }
}
