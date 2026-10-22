package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class DriverControllerIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void registerReturns201WithSnakeCaseFields() throws Exception {
        ResponseEntity<String> response = register(uniqueDocument());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("full_name").asText()).isEqualTo("Carlos Pérez");
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void registerDuplicateDocumentReturns409() {
        String documentNumber = uniqueDocument();
        register(documentNumber);

        ResponseEntity<String> response = register(documentNumber);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("DUPLICATE_DRIVER");
    }

    @Test
    void registerInvalidBodyReturns400WithFieldErrors() {
        String body = """
                {"full_name":"Al","document_number":"abc","email":"no-es-correo"}
                """;

        ResponseEntity<String> response = client().post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR").contains("\"errors\"");
    }

    @Test
    void getExistingDriverReturns200() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(id);
    }

    @Test
    void getUnknownDriverReturns404() {
        ResponseEntity<String> response = client().get().uri("/{id}", UUID.randomUUID())
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("DRIVER_NOT_FOUND");
    }

    @Test
    void getWithMalformedIdReturns400() {
        ResponseEntity<String> response = client().get().uri("/no-es-uuid").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listWithSizeAboveLimitReturns400() {
        ResponseEntity<String> response = client().get().uri("?size=500").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR");
    }

    @Test
    void updateChangesEditableFieldsAndReturns200() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();
        String body = """
                {"full_name":"Carlos A. Pérez","email":"nuevo@correo.com","phone":null}
                """;

        ResponseEntity<String> response = client().put().uri("/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).get("full_name").asText()).isEqualTo("Carlos A. Pérez");
    }

    private ResponseEntity<String> register(String documentNumber) {
        String body = """
                {"full_name":"Carlos Pérez","document_number":"%s","email":"carlos@correo.com","phone":"3001234567"}
                """.formatted(documentNumber);
        return client().post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port + "/ms-conductores/drivers")
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    private static String uniqueDocument() {
        return String.format("%010d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }
}
