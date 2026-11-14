package co.edu.univalle.conductores.application.service;

import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.application.port.out.ProcessedEventRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Servicio que procesa eventos asíncronos de conductores garantizando idempotencia.
 */
public class ProcessDriverEventService implements ProcessDriverEvent {

    public static final String DOCUMENT_EXPIRED_EVENT = "document_expired_event";
    public static final String DOCUMENT_RENEWED_EVENT = "document_renewed_event";
    public static final String SEVERE_INCIDENT_REPORTED_EVENT = "severe_incident_reported_event";

    private final DriverRepository driverRepository;
    private final ProcessedEventRepositoryPort processedEventRepository;
    private final ConsumerMetricsPort consumerMetrics;

    /**
     * Construye el servicio de procesamiento de eventos.
     *
     * @param driverRepository repositorio de conductores
     * @param processedEventRepository repositorio de eventos procesados
     * @param consumerMetrics puerto de métricas del consumidor
     */
    public ProcessDriverEventService(
            DriverRepository driverRepository,
            ProcessedEventRepositoryPort processedEventRepository,
            ConsumerMetricsPort consumerMetrics) {
        this.driverRepository = Objects.requireNonNull(driverRepository, "driverRepository");
        this.processedEventRepository = Objects.requireNonNull(processedEventRepository, "processedEventRepository");
        this.consumerMetrics = Objects.requireNonNull(consumerMetrics, "consumerMetrics");
    }

    @Override
    public EventProcessingResult process(Command command) {
        if (processedEventRepository.isProcessed(command.eventId())) {
            consumerMetrics.recordConsumed(command.eventType(), "duplicate");
            return EventProcessingResult.DUPLICATE;
        }

        Driver driver = driverRepository.findById(command.driverId())
                .orElseThrow(() -> new DriverNotFoundException(command.driverId()));

        applyEvent(driver, command.eventType(), command.occurredAt());

        driverRepository.save(driver);
        processedEventRepository.saveProcessed(command.eventId(), command.eventType(), command.driverId());
        consumerMetrics.recordConsumed(command.eventType(), "processed");
        return EventProcessingResult.PROCESSED;
    }

    private void applyEvent(Driver driver, String eventType, OffsetDateTime occurredAt) {
        switch (eventType) {
            case DOCUMENT_EXPIRED_EVENT -> driver.applyDocumentBlock(occurredAt);
            case DOCUMENT_RENEWED_EVENT -> driver.removeDocumentBlock(occurredAt);
            case SEVERE_INCIDENT_REPORTED_EVENT -> driver.applyMedicalBlock(occurredAt);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        }
    }
}

