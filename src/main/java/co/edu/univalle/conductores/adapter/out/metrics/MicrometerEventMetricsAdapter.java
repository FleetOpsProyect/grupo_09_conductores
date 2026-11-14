package co.edu.univalle.conductores.adapter.out.metrics;

import co.edu.univalle.conductores.application.port.out.EventMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Adapter Micrometer para las métricas de publicación y pendientes del outbox.
 */
@Component
public class MicrometerEventMetricsAdapter implements EventMetricsPort {

    private static final String PUBLISHED_METRIC = "fleetops_driver_events_published";
    private static final String FAILURE_METRIC = "fleetops_driver_event_publish_failures";

    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingEvents = new AtomicLong();

    /**
     * Registra el gauge de eventos pendientes.
     *
     * @param meterRegistry registro Micrometer de la aplicación
     */
    public MicrometerEventMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("fleetops_outbox_pending_events", pendingEvents, AtomicLong::doubleValue)
                .description("Número de eventos pendientes de publicación en Firestore")
                .register(meterRegistry);
    }

    @Override
    public void recordPublished(String eventType) {
        Counter.builder(PUBLISHED_METRIC)
                .tag("event_type", eventType)
                .description("Eventos del outbox publicados y marcados SENT")
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void recordPublicationFailure(String eventType) {
        Counter.builder(FAILURE_METRIC)
                .tag("event_type", eventType)
                .description("Intentos fallidos de publicación en SNS")
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void setPendingEvents(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count no puede ser negativo");
        }
        pendingEvents.set(count);
    }
}
