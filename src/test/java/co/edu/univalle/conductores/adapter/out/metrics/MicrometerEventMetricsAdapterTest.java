package co.edu.univalle.conductores.adapter.out.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerEventMetricsAdapterTest {

    private static final String EVENT_TYPE = "driver_activated_event";

    @Test
    void recordsPublicationCountersAndPendingGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerEventMetricsAdapter metrics = new MicrometerEventMetricsAdapter(registry);

        metrics.recordPublished(EVENT_TYPE);
        metrics.recordPublicationFailure(EVENT_TYPE);
        metrics.setPendingEvents(4);

        assertThat(registry.get("fleetops_driver_events_published")
                .tag("event_type", EVENT_TYPE)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get("fleetops_driver_event_publish_failures")
                .tag("event_type", EVENT_TYPE)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get("fleetops_outbox_pending_events").gauge().value()).isEqualTo(4.0);
    }
}
