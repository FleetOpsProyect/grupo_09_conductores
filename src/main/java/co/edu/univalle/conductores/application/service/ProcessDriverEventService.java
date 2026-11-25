package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.DriverBlockedPayload;
import co.edu.univalle.conductores.application.event.DriverEventType;
import co.edu.univalle.conductores.application.event.EventEnvelope;
import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.ProcessedEventRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Servicio que procesa eventos asíncronos de conductores garantizando idempotencia.
 *
 * <p>El tipo de evento se valida antes de tocar la base de datos, de modo que un tipo desconocido
 * siempre se rechaza como mensaje inválido. El cambio de estado, el registro en processed_events y, si
 * corresponde, el evento en outbox_events se guardan en la misma transacción. Un vencimiento de documento
 * que deja de tener ACTIVE al conductor guarda driver_blocked_event (HU-04).
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
        Function<Driver, OutboxEvent> change = changeFor(command);

        boolean applied = processedEventRepository.applyOnce(
                command.eventId(), command.eventType(), command.driverId(), clock.instant(), change);

        if (!applied) {
            consumerMetrics.recordConsumed(command.eventType(), "duplicate");
            return EventProcessingResult.DUPLICATE;
        }
        consumerMetrics.recordConsumed(command.eventType(), "processed");
        return EventProcessingResult.PROCESSED;
    }

    /**
     * Change to apply inside the transaction, on the driver as it is stored when it runs. It returns the
     * outbox event to store with the change, or null.
     */
    private Function<Driver, OutboxEvent> changeFor(Command command) {
        OffsetDateTime occurredAt = command.occurredAt();
        return switch (command.eventType()) {
            case DOCUMENT_EXPIRED_EVENT -> driver -> {
                boolean wasActive = driver.getStatus() == DriverStatus.ACTIVE;
                driver.applyDocumentBlock(occurredAt);
                return wasActive ? blockedEvent(driver, command.correlationId()) : null;
            };
            case DOCUMENT_RENEWED_EVENT -> driver -> {
                driver.removeDocumentBlock(occurredAt);
                return null;
            };
            case SEVERE_INCIDENT_REPORTED_EVENT -> driver -> {
                driver.applyMedicalBlock(occurredAt);
                return null;
            };
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + command.eventType());
        };
    }

    /**
     * driver_blocked_event for a driver that stopped being ACTIVE, with the correlation_id of the consumed event.
     */
    private OutboxEvent blockedEvent(Driver driver, UUID correlationId) {
        EventEnvelope<DriverBlockedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                DriverEventType.DRIVER_BLOCKED.value(),
                OffsetDateTime.now(clock),
                "driver",
                correlationId,
                new DriverBlockedPayload(driver.getId(), driver.getFullName()));
        return OutboxEvent.pending(envelope);
    }
}
