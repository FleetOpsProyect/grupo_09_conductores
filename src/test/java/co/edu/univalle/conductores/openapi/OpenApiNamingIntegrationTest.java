package co.edu.univalle.conductores.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class OpenApiNamingIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void schemasUseSnakeCasePropertyNames() throws Exception {
        ResponseEntity<String> response = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/v3/api-docs")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode schemas = MAPPER.readTree(response.getBody()).path("components").path("schemas");
        assertThat(properties(schemas, "DriverResponse"))
                .contains("full_name", "document_number", "created_at", "updated_at")
                .doesNotContain("fullName", "documentNumber", "createdAt", "updatedAt");
        assertThat(properties(schemas, "RegisterDriverRequest"))
                .contains("full_name", "document_number")
                .doesNotContain("fullName", "documentNumber");
        assertThat(properties(schemas, "UpdateDriverRequest"))
                .contains("full_name")
                .doesNotContain("fullName");
    }

    private static Set<String> properties(JsonNode schemas, String schema) {
        Set<String> names = new HashSet<>();
        schemas.path(schema).path("properties").fieldNames().forEachRemaining(names::add);
        return names;
    }
}
