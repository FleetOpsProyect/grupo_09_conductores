package co.edu.univalle.conductores.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class MetricsEndpointIntegrationTest extends BaseIntegrationTest {

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
    }
}
