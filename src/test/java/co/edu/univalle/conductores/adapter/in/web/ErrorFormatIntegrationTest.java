package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.config.FirestoreConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * End-to-end check of the common error format (TEC_04-5): every error response goes through the
 * real server and has exactly {@code code}, {@code message}, {@code timestamp} (ISO 8601, -05:00)
 * and {@code path}, plus {@code errors} only for INVALID_DATA, and none of Spring's default fields.
 *
 * <p>The 503 case stops a Firestore emulator. So that this never affects other test classes, this
 * class runs against its own emulator: the {@code firestore} bean is replaced by a client pointing
 * to {@link #OWN_EMULATOR}. That gives this class its own Spring context, which is closed after
 * the class. The inherited warm-up runs on that client before the first case. The 503 case runs
 * last and is the only one that stops the container; nothing is restarted, because a restarted
 * container gets a new mapped port.
 *
 * <p>Details already verified elsewhere (field codes in {@code DriverControllerIntegrationTest},
 * the {@code Allow} header and the logs in {@code GlobalExceptionHandlerWebTest}) are not repeated.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ErrorFormatIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROJECT_ID = "demo-conductores";

    private static final List<String> COMMON_KEYS = List.of("code", "message", "timestamp", "path");

    private static final List<String> SPRING_DEFAULT_KEYS = List.of("status", "error", "trace");

    private static final GenericContainer<?> OWN_EMULATOR = newFirestoreEmulator();

    static {
        OWN_EMULATOR.start();
    }

    @TestBean(name = "firestore", methodName = "isolatedFirestore")
    private Firestore firestore;

    @LocalServerPort
    private int port;

    /**
     * Factory for the {@code firestore} bean override. Reuses the production client configuration,
     * pointed at this class's own emulator.
     *
     * @return Firestore client for {@link #OWN_EMULATOR}
     */
    static Firestore isolatedFirestore() {
        return new FirestoreConfig().firestore(PROJECT_ID, ownEmulatorHost());
    }

    /**
     * Checks that the context uses this class's own emulator. The warm-up of that client is the
     * inherited {@link BaseIntegrationTest#warmUpFirestore}, which runs before this method.
     *
     * @param firestore Firestore client of this class's context
     */
    @BeforeAll
    static void usesOwnEmulator(@Autowired Firestore firestore) {
        assertThat(firestore.getOptions().getEmulatorHost()).isEqualTo(ownEmulatorHost());
    }

    @Test
    void invalidBodyReturns400InvalidDataWithFieldErrors() throws Exception {
        ResponseEntity<String> response = client().post().uri("/drivers")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"full_name":"Al","document_number":"abc","email":"no-es-correo"}
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = assertErrorFormat(response, "INVALID_DATA", "Hay datos inválidos en la solicitud",
                "/drivers", true);
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("errors")).isNotEmpty();
        body.get("errors").forEach(error -> assertThat(keys(error)).containsExactlyInAnyOrder("field", "code"));
    }

    @Test
    void malformedJsonReturns400InvalidJson() throws Exception {
        ResponseEntity<String> response = client().post().uri("/drivers")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"full_name\":")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorFormat(response, "INVALID_JSON", "El cuerpo de la solicitud no es un JSON válido",
                "/drivers", false);
    }

    @Test
    void unknownDriverIdReturns404DriverNotFound() throws Exception {
        UUID id = UUID.randomUUID();

        ResponseEntity<String> response = get("/drivers/" + id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertErrorFormat(response, "DRIVER_NOT_FOUND", "No existe un conductor con id '" + id + "'",
                "/drivers/" + id, false);
    }

    @Test
    void nonUuidDriverIdReturns404DriverNotFound() throws Exception {
        ResponseEntity<String> response = get("/drivers/abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertErrorFormat(response, "DRIVER_NOT_FOUND", "No existe un conductor con id 'abc'",
                "/drivers/abc", false);
    }

    @Test
    void unknownRouteReturns404RouteNotFound() throws Exception {
        ResponseEntity<String> response = get("/does_not_exist");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertErrorFormat(response, "ROUTE_NOT_FOUND", "Ruta no encontrada", "/does_not_exist", false);
    }

    @Test
    void unsupportedMethodReturns405MethodNotAllowed() throws Exception {
        ResponseEntity<String> response = client().delete().uri("/drivers").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertErrorFormat(response, "METHOD_NOT_ALLOWED", "Método no permitido para esta ruta", "/drivers", false);
    }

    /** Runs last: it is the only case that stops the emulator, and it is not restarted. */
    @Test
    @Order(Integer.MAX_VALUE)
    void stoppedFirestoreReturns503ServiceUnavailable() throws Exception {
        OWN_EMULATOR.stop();
        UUID id = UUID.randomUUID();

        ResponseEntity<String> response = get("/drivers/" + id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertErrorFormat(response, "SERVICE_UNAVAILABLE", "Servicio temporalmente no disponible, intente nuevamente",
                "/drivers/" + id, false);
        assertThat(response.getBody()).doesNotContain("Exception", "at co.edu", "at com.google");
    }

    /**
     * Asserts the common error format and returns the parsed body.
     *
     * @param response HTTP response
     * @param code expected error code
     * @param message expected message
     * @param path expected request path
     * @param withErrors whether the {@code errors} field must be present
     * @return parsed body
     */
    private static JsonNode assertErrorFormat(ResponseEntity<String> response, String code, String message,
            String path, boolean withErrors) throws Exception {
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode body = MAPPER.readTree(response.getBody());

        List<String> expectedKeys = new ArrayList<>(COMMON_KEYS);
        if (withErrors) {
            expectedKeys.add("errors");
        }
        assertThat(keys(body)).containsExactlyInAnyOrderElementsOf(expectedKeys);
        SPRING_DEFAULT_KEYS.forEach(key -> assertThat(body.has(key)).as("field %s", key).isFalse());

        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").asText()).isEqualTo(message);
        assertThat(body.get("path").asText()).isEqualTo(path);

        String timestamp = body.get("timestamp").asText();
        OffsetDateTime parsed = OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertThat(parsed.getOffset().getId()).isEqualTo("-05:00");
        assertThat(timestamp).endsWith("-05:00");
        return body;
    }

    private static List<String> keys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static String ownEmulatorHost() {
        return OWN_EMULATOR.getHost() + ":" + OWN_EMULATOR.getMappedPort(FIRESTORE_PORT);
    }

    private ResponseEntity<String> get(String uri) {
        return client().get().uri(uri).retrieve().toEntity(String.class);
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }
}
