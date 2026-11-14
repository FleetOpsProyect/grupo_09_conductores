package co.edu.univalle.conductores.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
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
}
