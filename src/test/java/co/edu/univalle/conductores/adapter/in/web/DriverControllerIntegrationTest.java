package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
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
    void registerInvalidBodyReturns400WithFieldErrors() throws Exception {
        ResponseEntity<String> response = post("""
                {"full_name":"Al","document_number":"abc","email":"no-es-correo"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "full_name", "INVALID_LENGTH",
                "document_number", "INVALID_FORMAT",
                "email", "INVALID_FORMAT"));
        body.get("errors").forEach(error -> assertThat(error.has("message")).isFalse());
    }

    @Test
    void registerMissingOrBlankRequiredFieldsReturnsRequired() throws Exception {
        ResponseEntity<String> response = post("""
                {"full_name":"","document_number":"1234567"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(body.get("errors")).hasSize(2);
        assertThat(fieldCodes(body)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "full_name", "REQUIRED",
                "email", "REQUIRED"));
    }

    @Test
    void registerMalformedJsonReturnsInvalidJson() throws Exception {
        ResponseEntity<String> response = post("{\"full_name\":");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_JSON");
        assertThat(body.has("errors")).isFalse();
    }

    @Test
    void registerWrongTypeInBodyReturnsInvalidType() throws Exception {
        ResponseEntity<String> response = post("""
                {"full_name":{},"document_number":"1234567","email":"carlos@correo.com"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("full_name", "INVALID_TYPE"));
    }

    @Test
    void registerEmailWithoutDomainExtensionReturnsInvalidFormat() throws Exception {
        ResponseEntity<String> response = post("""
                {"full_name":"Ana Gómez","document_number":"%s","email":"ana@localhost"}
                """.formatted(uniqueDocument()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("email", "INVALID_FORMAT"));
    }

    @Test
    void registerEmailWithSingleLetterExtensionReturns201() throws Exception {
        ResponseEntity<String> response = post("""
                {"full_name":"Ana Gómez","document_number":"%s","email":"ana@x.c"}
                """.formatted(uniqueDocument()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(MAPPER.readTree(response.getBody()).get("email").asText()).isEqualTo("ana@x.c");
    }

    @Test
    void registerShortNameAfterTrimReturnsInvalidLength() throws Exception {
        ResponseEntity<String> response = post("""
                {"full_name":"  A  ","document_number":"%s","email":"ana@correo.com"}
                """.formatted(uniqueDocument()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("full_name", "INVALID_LENGTH"));
    }

    @Test
    void registerNameWithSurroundingSpacesIsValidAndTrimmed() throws Exception {
        ResponseEntity<String> response = post("""
                {"full_name":"  Ana  ","document_number":"%s","email":"ana@correo.com"}
                """.formatted(uniqueDocument()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(MAPPER.readTree(response.getBody()).get("full_name").asText()).isEqualTo("Ana");
    }

    @Test
    void updateEmailWithoutDomainExtensionReturnsInvalidFormat() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos Pérez","email":"ana@localhost"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("email", "INVALID_FORMAT"));
    }

    @Test
    void updateShortNameAfterTrimReturnsInvalidLength() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"  A  ","email":"carlos@correo.com"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("full_name", "INVALID_LENGTH"));
    }

    @Test
    void updateNameWithSurroundingSpacesIsValidAndTrimmed() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"  Ana  ","email":"carlos@correo.com"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).get("full_name").asText()).isEqualTo("Ana");
    }

    @Test
    void getExistingDriverReturns200() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(id);
    }

    @Test
    void getUnknownDriverReturns404() throws Exception {
        UUID id = UUID.randomUUID();

        ResponseEntity<String> response = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertDriverNotFound(response, id.toString());
    }

    @Test
    void getWithMalformedIdReturns404() throws Exception {
        ResponseEntity<String> response = client().get().uri("/abc").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertDriverNotFound(response, "abc");
    }

    // The strict regex rejects the non-canonical form and the API responds 404 DRIVER_NOT_FOUND.
    @Test
    void getWithNonCanonicalUuidReturns404() throws Exception {
        ResponseEntity<String> response = client().get().uri("/1-2-3-4-5").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertDriverNotFound(response, "1-2-3-4-5");
    }

    @Test
    void putWithMalformedIdReturns404() throws Exception {
        ResponseEntity<String> response = put("abc", """
                {"full_name":"Carlos Pérez","email":"carlos@correo.com"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertDriverNotFound(response, "abc");
    }

    // The body is validated before the method runs, so an invalid body wins over a malformed id.
    @Test
    void putWithMalformedIdAndInvalidBodyReturns400() throws Exception {
        ResponseEntity<String> response = put("abc", """
                {"full_name":"Carlos Pérez","email":"ana@localhost"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("email", "INVALID_FORMAT"));
    }

    @Test
    void listWithSizeAboveLimitReturns400() throws Exception {
        ResponseEntity<String> response = client().get().uri("?size=500").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("size", "VALUE_NOT_ALLOWED"));
    }

    @Test
    void listWithUnknownStatusReturnsValueNotAllowed() throws Exception {
        ResponseEntity<String> response = client().get().uri("?status=FOO").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("status", "VALUE_NOT_ALLOWED"));
    }

    @Test
    void listWithNonNumericPageReturnsInvalidType() throws Exception {
        ResponseEntity<String> response = client().get().uri("?page=abc").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("page", "INVALID_TYPE"));
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

    private ResponseEntity<String> post(String body) {
        return client().post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> put(String id, String body) {
        return client().put().uri("/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private static void assertDriverNotFound(ResponseEntity<String> response, String id) throws Exception {
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("DRIVER_NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo("No existe un conductor con id '" + id + "'");
    }

    private static Map<String, String> fieldCodes(JsonNode body) {
        Map<String, String> codes = new HashMap<>();
        body.get("errors").forEach(error -> codes.put(error.get("field").asText(), error.get("code").asText()));
        return codes;
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port + "/drivers")
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    private static String uniqueDocument() {
        return String.format("%010d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }
}
