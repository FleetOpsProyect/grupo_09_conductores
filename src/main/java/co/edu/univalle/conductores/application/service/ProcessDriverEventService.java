package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.event.DriverActivatedPayload;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Servicio que procesa eventos asíncronos de conductores garantizando idempotencia.
 *
 * <p>El tipo de evento se valida antes de tocar la base de datos, de modo que un tipo desconocido
 * siempre se rechaza como mensaje inválido. El cambio de estado, el registro en processed_events y, si
 * corresponde, el evento en outbox_events se guardan en la misma transacción. Un vencimiento de documento
 * que deja de tener ACTIVE al conductor guarda driver_blocked_event (HU-04). Una renovación que quita el
 * bloqueo por documentos guarda driver_activated_event si el conductor queda ACTIVE (HU-05).
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
        AtomicBoolean changed = new AtomicBoolean(true);
        Function<Driver, OutboxEvent> change = changeFor(command, changed);

        boolean applied = processedEventRepository.applyOnce(
                command.eventId(), command.eventType(), command.driverId(), clock.instant(), change);

        if (!applied) {
            consumerMetrics.recordConsumed(command.eventType(), "duplicate");
            return EventProcessingResult.DUPLICATE;
        }
        consumerMetrics.recordConsumed(command.eventType(), "processed");
        return changed.get() ? EventProcessingResult.PROCESSED : EventProcessingResult.PROCESSED_WITHOUT_CHANGE;
    }

    /**
     * Change to apply inside the transaction, on the driver as it is stored when it runs. It returns the
     * outbox event to store with the change, or null.
     */
    private Function<Driver, OutboxEvent> changeFor(Command command, AtomicBoolean changed) {
        OffsetDateTime occurredAt = command.occurredAt();
        return switch (command.eventType()) {
            case DOCUMENT_EXPIRED_EVENT -> driver -> {
                boolean wasActive = driver.getStatus() == DriverStatus.ACTIVE;
                driver.applyDocumentBlock(occurredAt);
                return wasActive ? blockedEvent(driver, command.correlationId()) : null;
            };
            case DOCUMENT_RENEWED_EVENT -> driver -> {
                // The transaction may run this change more than once; the last run is the one committed.
                boolean wasBlocked = driver.getStatus() != DriverStatus.ACTIVE;
                changed.set(driver.removeDocumentBlock(occurredAt));
                boolean reactivated = wasBlocked && driver.getStatus() == DriverStatus.ACTIVE;
                return reactivated ? activatedEvent(driver, command.correlationId()) : null;
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
        return pendingEvent(DriverEventType.DRIVER_BLOCKED,
                new DriverBlockedPayload(driver.getId(), driver.getFullName()), correlationId);
    }

    /**
     * driver_activated_event for a driver that is ACTIVE again after a document renewal, with the correlation_id
     * of the consumed event.
     */
    private OutboxEvent activatedEvent(Driver driver, UUID correlationId) {
        return pendingEvent(DriverEventType.DRIVER_ACTIVATED,
                new DriverActivatedPayload(driver.getId(), driver.getFullName()), correlationId);
    }

    private <T> OutboxEvent pendingEvent(DriverEventType type, T payload, UUID correlationId) {
        EventEnvelope<T> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                type.value(),
                OffsetDateTime.now(clock),
                "driver",
                correlationId,
                payload);
        return OutboxEvent.pending(envelope);
    }
}
