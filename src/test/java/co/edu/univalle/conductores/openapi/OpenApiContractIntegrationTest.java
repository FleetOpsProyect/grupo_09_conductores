package co.edu.univalle.conductores.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * Verifica que la especificación OpenAPI publique el contrato de las historias de usuario (TEC-02).
 */
class OpenApiContractIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    private JsonNode spec;

    @BeforeEach
    void loadSpec() throws Exception {
        String body = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/v3/api-docs")
                .retrieve()
                .body(String.class);
        spec = MAPPER.readTree(body);
    }

    @Test
    void publishesTitleVersionAndBearerScheme() {
        assertThat(spec.at("/info/title").asText())
                .isEqualTo("FleetOps - Microservicio de Conductores (G09)");
        assertThat(spec.at("/info/version").asText()).isEqualTo("1.0.0");
        JsonNode bearer = spec.at("/components/securitySchemes/bearerAuth");
        assertThat(bearer.path("type").asText()).isEqualTo("http");
        assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(spec.path("security").toString()).contains("bearerAuth");
    }

    @Test
    void responseCodesMatchUserStories() {
        assertThat(codes("/drivers", "post")).containsExactlyInAnyOrder("201", "400", "403", "409", "503");
        assertThat(codes("/drivers", "get")).containsExactlyInAnyOrder("200", "400", "403", "503");
        assertThat(codes("/drivers/{id}", "get")).containsExactlyInAnyOrder("200", "403", "404", "503");
        assertThat(codes("/drivers/{id}", "put")).containsExactlyInAnyOrder("200", "400", "404", "503");
    }

    @Test
    void errorSchemaIncludesFieldErrors() {
        JsonNode schemas = spec.at("/components/schemas");
        assertThat(properties(schemas.path("ErrorResponse")))
                .contains("code", "message", "timestamp", "path", "errors");
        assertThat(spec.at("/paths/~1drivers/post/responses/409/content").toString())
                .contains("ErrorResponse");
    }

    @Test
    void ownRecordRuleIsMarkedAsPhaseTwo() {
        String description = spec.at("/paths/~1drivers~1{id}/get/description").asText();
        assertThat(description).contains("solo el propio", "custom:driver_id", "403", "ACCESS_DENIED",
                "Fase 2, no implementado");
    }

    private Set<String> codes(String path, String method) {
        Set<String> codes = new HashSet<>();
        spec.path("paths").path(path).path(method).path("responses").fieldNames().forEachRemaining(codes::add);
        return codes;
    }

    private static Set<String> properties(JsonNode schema) {
        Set<String> names = new HashSet<>();
        schema.path("properties").fieldNames().forEachRemaining(names::add);
        return names;
    }
}
