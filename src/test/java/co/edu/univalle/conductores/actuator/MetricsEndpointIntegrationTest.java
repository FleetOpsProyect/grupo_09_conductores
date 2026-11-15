package co.edu.univalle.conductores.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.EventMetricsPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class MetricsEndpointIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventMetricsPort eventMetrics;

    @Autowired
    private ConsumerMetricsPort consumerMetrics;

    @LocalServerPort
    private int port;

    @Test
    void metricsAreExposedAtMetricsPath() {
        ResponseEntity<String> response = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/metrics")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
        assertThat(response.getBody()).contains("fleetops_outbox_pending_events");
    }

    @Test
    void eventPublicationMetricsAreExposedInPrometheusFormat() {
        eventMetrics.recordPublished("driver_activated_event");
        eventMetrics.recordPublicationFailure("driver_activated_event");
        eventMetrics.setPendingEvents(3);

        ResponseEntity<String> response = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/metrics")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("fleetops_driver_events_published_total")
                .contains("fleetops_driver_event_publish_failures_total")
                .contains("fleetops_outbox_pending_events");
    }

    @Test
    void eventConsumerMetricsAreExposedInPrometheusFormat() {
        consumerMetrics.recordConsumed("document_expired_event", "processed");
        consumerMetrics.recordConsumed("document_expired_event", "duplicate");
        consumerMetrics.recordDlq("document_expired_event", "invalid_message");
        consumerMetrics.recordDlq("document_expired_event", "driver_not_found");
        consumerMetrics.recordDlq("document_expired_event", "max_retries");

        ResponseEntity<String> response = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/metrics")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("drivers_events_consumed_total{event_type=\"document_expired_event\",result=\"processed\"")
                .contains("drivers_events_consumed_total{event_type=\"document_expired_event\",result=\"duplicate\"")
                .contains("drivers_events_dlq_total{event_type=\"document_expired_event\",reason=\"invalid_message\"")
                .contains("drivers_events_dlq_total{event_type=\"document_expired_event\",reason=\"driver_not_found\"")
                .contains("drivers_events_dlq_total{event_type=\"document_expired_event\",reason=\"max_retries\"");
    }
}
