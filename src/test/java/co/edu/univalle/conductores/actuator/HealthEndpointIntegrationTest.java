package co.edu.univalle.conductores.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.health.RabbitHealthIndicator;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class HealthEndpointIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void healthReturnsOkWhenFirestoreIsAvailable() {
        ResponseEntity<String> response = client().get().uri("/health").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void rabbitHealthIndicatorIsDisabled() {
        assertThat(applicationContext.getBeansOfType(RabbitHealthIndicator.class)).isEmpty();
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }
}
