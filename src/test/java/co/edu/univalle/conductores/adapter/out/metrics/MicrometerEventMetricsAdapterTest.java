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

    @Test
    void recordsConsumedAndDlqCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerEventMetricsAdapter metrics = new MicrometerEventMetricsAdapter(registry);

        metrics.recordConsumed("document_expired_event", "processed");
        metrics.recordConsumed("document_expired_event", "duplicate");
        metrics.recordDlq("document_expired_event", "invalid_message");
        metrics.recordDlq("document_expired_event", "driver_not_found");
        metrics.recordDlq("document_expired_event", "max_retries");

        assertThat(registry.get("drivers_events_consumed")
                .tag("event_type", "document_expired_event")
                .tag("result", "processed")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("drivers_events_consumed")
                .tag("event_type", "document_expired_event")
                .tag("result", "duplicate")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("drivers_events_dlq")
                .tag("event_type", "document_expired_event")
                .tag("reason", "invalid_message")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("drivers_events_dlq")
                .tag("event_type", "document_expired_event")
                .tag("reason", "driver_not_found")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("drivers_events_dlq")
                .tag("event_type", "document_expired_event")
                .tag("reason", "max_retries")
                .counter().count()).isEqualTo(1.0);
    }
}
