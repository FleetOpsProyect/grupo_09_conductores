package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.ProcessedEventRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Servicio que procesa eventos asíncronos de conductores garantizando idempotencia.
 *
 * <p>El tipo de evento se valida antes de tocar la base de datos, de modo que un tipo desconocido
 * siempre se rechaza como mensaje inválido. El cambio de estado y el registro en processed_events se
 * guardan en la misma transacción.
 */
public class ProcessDriverEventService implements ProcessDriverEvent {

    private final ProcessedEventRepositoryPort processedEventRepository;
    private final ConsumerMetricsPort consumerMetrics;
    private final Clock clock;

    /**
     * Construye el servicio de procesamiento de eventos.
     *
     * @param processedEventRepository repositorio que aplica cada evento una sola vez
     * @param consumerMetrics puerto de métricas del consumidor
     * @param clock reloj para la fecha de procesamiento
     */
    public ProcessDriverEventService(
            ProcessedEventRepositoryPort processedEventRepository,
            ConsumerMetricsPort consumerMetrics,
            Clock clock) {
        this.processedEventRepository = Objects.requireNonNull(processedEventRepository, "processedEventRepository");
        this.consumerMetrics = Objects.requireNonNull(consumerMetrics, "consumerMetrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EventProcessingResult process(Command command) {
        Consumer<Driver> change = changeFor(command.eventType(), command.occurredAt());

        boolean applied = processedEventRepository.applyOnce(
                command.eventId(), command.eventType(), command.driverId(), clock.instant(), change);

        if (!applied) {
            consumerMetrics.recordConsumed(command.eventType(), "duplicate");
            return EventProcessingResult.DUPLICATE;
        }
        consumerMetrics.recordConsumed(command.eventType(), "processed");
        return EventProcessingResult.PROCESSED;
    }

    private static Consumer<Driver> changeFor(String eventType, OffsetDateTime occurredAt) {
        return switch (eventType) {
            case DOCUMENT_EXPIRED_EVENT -> driver -> driver.applyDocumentBlock(occurredAt);
            case DOCUMENT_RENEWED_EVENT -> driver -> driver.removeDocumentBlock(occurredAt);
            case SEVERE_INCIDENT_REPORTED_EVENT -> driver -> driver.applyMedicalBlock(occurredAt);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        };
    }
}
