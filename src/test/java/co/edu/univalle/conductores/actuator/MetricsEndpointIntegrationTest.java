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
        assertThat(response.getBody()).contains("drivers_outbox_pending");
    }

    @Test
    void httpRequestHistogramsAreExposedWithPrometheusNamesAndTags() {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        ResponseEntity<String> driversResponse = client.get()
                .uri("/drivers")
                .retrieve()
                .toEntity(String.class);
        assertThat(driversResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> metricsResponse = client.get()
                .uri("/metrics")
                .retrieve()
                .toEntity(String.class);

        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String metrics = metricsResponse.getBody();
        assertThat(metrics.lines()
                .filter(line -> line.startsWith("http_server_requests_seconds_count{")
                        && line.contains("uri=\"/drivers\""))
                .toList())
                .singleElement()
                .satisfies(line -> assertThat(line).contains("method=\"GET\"", "status=\"200\""));
        assertThat(metrics.lines()
                .filter(line -> line.startsWith("http_server_requests_seconds_bucket{")
                        && line.contains("uri=\"/drivers\"")
                        && line.contains("le=\"2.0\""))
                .toList())
                .singleElement()
                .satisfies(line -> assertThat(line).contains("method=\"GET\"", "status=\"200\""));
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
                .contains("drivers_events_published_total{event_type=\"driver_activated_event\"")
                .contains("drivers_events_publish_failures_total{event_type=\"driver_activated_event\"")
                .contains("drivers_outbox_pending 3.0")
                .doesNotContain("fleetops_");
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
