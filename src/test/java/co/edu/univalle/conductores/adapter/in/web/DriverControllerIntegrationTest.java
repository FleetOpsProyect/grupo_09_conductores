package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import com.google.cloud.firestore.Firestore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

class DriverControllerIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @MockitoSpyBean
    private DriverRepository driverRepository;

    @Autowired
    private Firestore firestore;

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
    void getExistingDriverReturnsRequiredFields() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("id").asText()).isEqualTo(id);
        assertThat(body.get("full_name").asText()).isNotBlank();
        assertThat(body.get("document_number").asText()).isNotBlank();
        assertThat(body.get("email").asText()).isNotBlank();
        assertThat(body.has("phone")).isTrue();
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void getDriverInactiveForExpiredDocumentsReturns200() throws Exception {
        UUID id = seedDriver(true, false);

        ResponseEntity<String> response = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).get("status").asText())
                .isEqualTo("INACTIVE_EXPIRED_DOCUMENTS");
    }

    @Test
    void getDriverWithMedicalHoldReturns200() throws Exception {
        UUID id = seedDriver(false, true);

        ResponseEntity<String> response = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).get("status").asText())
                .isEqualTo("MEDICAL_HOLD_INCIDENT");
    }

    @Test
    void getDriverReturnsSameResponseWithOrWithoutAuthorizationHeader() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> withoutToken = client().get().uri("/{id}", id)
                .retrieve().toEntity(String.class);

        ResponseEntity<String> withToken = client().get().uri("/{id}", id)
                .header("Authorization", "Bearer token-de-prueba")
                .retrieve().toEntity(String.class);

        assertThat(withToken.getStatusCode()).isEqualTo(withoutToken.getStatusCode());
        assertThat(withToken.getBody()).isEqualTo(withoutToken.getBody());
    }

    @Test
    void getUnknownDriverReturnsSameResponseWithOrWithoutAuthorizationHeader() throws Exception {
        UUID id = UUID.randomUUID();

        ResponseEntity<String> withoutToken = client().get().uri("/{id}", id)
                .retrieve().toEntity(String.class);

        ResponseEntity<String> withToken = client().get().uri("/{id}", id)
                .header("Authorization", "Bearer token-de-prueba")
                .retrieve().toEntity(String.class);

        assertThat(withoutToken.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(withToken.getStatusCode()).isEqualTo(withoutToken.getStatusCode());
        assertDriverNotFound(withoutToken, id.toString());
        assertDriverNotFound(withToken, id.toString());
    }

    private UUID seedDriver(boolean documentBlock, boolean medicalBlock) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(-5));
        driverRepository.save(Driver.restore(id, uniqueDocument(), "Carlos Pérez", "carlos@correo.com",
                "3001234567", documentBlock, medicalBlock, now, now));
        return id;
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
    void listWithSeveralInvalidParametersReturnsOneErrorPerParameter() throws Exception {
        ResponseEntity<String> response = client().get().uri("?size=500&page=-1").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(body.get("errors")).hasSize(2);
        assertThat(fieldCodes(body))
                .isEqualTo(Map.of("size", "VALUE_NOT_ALLOWED", "page", "VALUE_NOT_ALLOWED"));
    }

    @Test
    void listTotalCountsAllMatchingDriversNotOnlyCurrentPage() throws Exception {
        long before = totalOf("status=MEDICAL_HOLD_INCIDENT");
        seedDriver(false, true);
        seedDriver(false, true);
        seedDriver(false, true);

        ResponseEntity<String> response = client().get()
                .uri("?status=MEDICAL_HOLD_INCIDENT&size=2")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("content")).hasSize(2);
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(2);
        assertThat(body.get("total").asLong()).isEqualTo(before + 3);
    }

    private long totalOf(String query) throws Exception {
        ResponseEntity<String> response = client().get().uri("?" + query).retrieve().toEntity(String.class);
        return MAPPER.readTree(response.getBody()).get("total").asLong();
    }

    @Test
    void listReturnsSameOrderOnTwoCallsWhenCreatedAtIsEqual() throws Exception {
        OffsetDateTime sameInstant = OffsetDateTime.now(ZoneOffset.ofHours(-5)).truncatedTo(ChronoUnit.MILLIS);
        List<String> seededIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            seededIds.add(id.toString());
            driverRepository.save(Driver.restore(id, uniqueDocument(), "Carlos Pérez", "carlos@correo.com",
                    "3001234567", true, false, sameInstant, sameInstant));
        }

        String first = client().get().uri("?status=INACTIVE_EXPIRED_DOCUMENTS&size=100")
                .retrieve().toEntity(String.class).getBody();
        String second = client().get().uri("?status=INACTIVE_EXPIRED_DOCUMENTS&size=100")
                .retrieve().toEntity(String.class).getBody();

        assertThat(second).isEqualTo(first);
        assertThat(idsIn(first)).containsAll(seededIds);
    }

    private List<String> idsIn(String listBody) throws Exception {
        List<String> ids = new ArrayList<>();
        MAPPER.readTree(listBody).get("content").forEach(driver -> ids.add(driver.get("id").asText()));
        return ids;
    }

    @Test
    void listPageBeyondLastPageReturns200WithEmptyContentAndRealTotal() throws Exception {
        seedDriver(false, true);
        seedDriver(false, true);
        long total = totalOf("status=MEDICAL_HOLD_INCIDENT&size=2");
        int beyondLastPage = (int) (total / 2) + 1;

        ResponseEntity<String> response = client().get()
                .uri("?status=MEDICAL_HOLD_INCIDENT&size=2&page=" + beyondLastPage)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.get("content")).isEmpty();
        assertThat(body.get("page").asInt()).isEqualTo(beyondLastPage);
        assertThat(body.get("total").asLong()).isEqualTo(total);
    }

    @Test
    void listIncludesActiveDriverWithoutLicenseData() throws Exception {
        UUID id = seedDriver(false, false);
        long total = totalOf("status=ACTIVE&size=100");
        int lastPage = (int) ((total - 1) / 100);

        ResponseEntity<String> response = client().get()
                .uri("?status=ACTIVE&size=100&page=" + lastPage)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idsIn(response.getBody())).contains(id.toString());
    }

    @Test
    void listWithoutValidUntilMakesNoOutboundCalls() throws Exception {
        AtomicInteger outboundCalls = new AtomicInteger();
        HttpServer simulatedExternalService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        simulatedExternalService.createContext("/", exchange -> {
            outboundCalls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        simulatedExternalService.start();
        try {
            ResponseEntity<String> response = client().get().uri("?status=ACTIVE&size=20")
                    .retrieve().toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(outboundCalls.get()).isZero();
        } finally {
            simulatedExternalService.stop(0);
        }
    }

    @Test
    void listWithoutParametersUsesDefaultPageAndSize() throws Exception {
        ResponseEntity<String> response = client().get().uri("").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(20);
        assertThat(body.get("content").size()).isLessThanOrEqualTo(20);
    }

    @Test
    void listOrdersByCreatedAtAscendingRegardlessOfInsertionOrder() throws Exception {
        OffsetDateTime first = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(-5));
        OffsetDateTime second = first.plusDays(1);
        OffsetDateTime third = first.plusDays(2);
        UUID idThird = saveInactiveDriverCreatedAt(third);
        UUID idFirst = saveInactiveDriverCreatedAt(first);
        UUID idSecond = saveInactiveDriverCreatedAt(second);

        String body = client().get().uri("?status=INACTIVE_EXPIRED_DOCUMENTS&size=100")
                .retrieve().toEntity(String.class).getBody();

        assertThat(idsIn(body)).containsSubsequence(idFirst.toString(), idSecond.toString(), idThird.toString());
    }

    @Test
    void listElementsHaveTheSameFieldsAsGetById() throws Exception {
        UUID id = seedDriver(false, true);

        String body = client().get().uri("?status=MEDICAL_HOLD_INCIDENT&size=100")
                .retrieve().toEntity(String.class).getBody();
        JsonNode element = null;
        for (JsonNode driver : MAPPER.readTree(body).get("content")) {
            if (driver.get("id").asText().equals(id.toString())) {
                element = driver;
            }
        }

        assertThat(element).isNotNull();
        List<String> fields = new ArrayList<>();
        element.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "id", "full_name", "document_number", "email", "phone", "status", "created_at", "updated_at");
    }

    private UUID saveInactiveDriverCreatedAt(OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        driverRepository.save(Driver.restore(id, uniqueDocument(), "Carlos Pérez", "carlos@correo.com",
                "3001234567", true, false, createdAt, createdAt));
        return id;
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
    
    @Test
    void updateWithoutPhoneKeyKeepsTheStoredPhone() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos A. Pérez","email":"nuevo@correo.com"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).get("phone").asText()).isEqualTo("3001234567");
        JsonNode stored = MAPPER.readTree(client().get().uri("/{id}", id).retrieve().body(String.class));
        assertThat(stored.get("phone").asText()).isEqualTo("3001234567");
        assertThat(stored.get("full_name").asText()).isEqualTo("Carlos A. Pérez");
    }

    @Test
    void updateWithNullPhoneErasesTheStoredPhone() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos Pérez","email":"carlos@correo.com","phone":null}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).get("phone").isNull()).isTrue();
        JsonNode stored = MAPPER.readTree(client().get().uri("/{id}", id).retrieve().body(String.class));
        assertThat(stored.get("phone").isNull()).isTrue();
    }

    @Test
    void updateWithPhoneValueReplacesTheStoredPhone() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos Pérez","email":"carlos@correo.com","phone":"3159876543"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode stored = MAPPER.readTree(client().get().uri("/{id}", id).retrieve().body(String.class));
        assertThat(stored.get("phone").asText()).isEqualTo("3159876543");
    }

    @Test
    void updateWithInvalidPhoneReturnsInvalidFormatAndKeepsTheStoredPhone() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos Pérez","email":"carlos@correo.com","phone":"2001234567"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldCodes(MAPPER.readTree(response.getBody())))
                .containsExactlyEntriesOf(Map.of("phone", "INVALID_FORMAT"));
        JsonNode stored = MAPPER.readTree(client().get().uri("/{id}", id).retrieve().body(String.class));
        assertThat(stored.get("phone").asText()).isEqualTo("3001234567");
    }

    @Test
    void updateWithoutRequiredFieldsReturnsRequiredForEachOne() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"phone":"3001234567"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyInAnyOrderEntriesOf(
                Map.of("full_name", "REQUIRED", "email", "REQUIRED"));
    }

    @Test
    void updateWithDifferentDocumentNumberReturnsNotEditableAndChangesNothing() throws Exception {
        String document = uniqueDocument();
        String id = MAPPER.readTree(register(document).getBody()).get("id").asText();
        String other = document.equals("0000000001") ? "0000000002" : "0000000001";

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos A. Pérez","email":"nuevo@correo.com","document_number":"%s"}
                """.formatted(other));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
        assertThat(fieldCodes(body)).containsExactlyEntriesOf(Map.of("document_number", "NOT_EDITABLE"));
        JsonNode stored = MAPPER.readTree(client().get().uri("/{id}", id).retrieve().body(String.class));
        assertThat(stored.get("document_number").asText()).isEqualTo(document);
        assertThat(stored.get("full_name").asText()).isEqualTo("Carlos Pérez");
    }

    @Test
    void updateWithSameDocumentNumberIsAccepted() throws Exception {
        String document = uniqueDocument();
        String id = MAPPER.readTree(register(document).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos A. Pérez","email":"carlos@correo.com","document_number":"%s"}
                """.formatted(document));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MAPPER.readTree(response.getBody()).get("full_name").asText()).isEqualTo("Carlos A. Pérez");
    }

    @Test
    void updateIgnoresStatusAndKeepsTheStoredOne() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Carlos A. Pérez","email":"nuevo@correo.com","status":"MEDICAL_HOLD_INCIDENT"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode stored = MAPPER.readTree(client().get().uri("/{id}", id).retrieve().body(String.class));
        assertThat(stored.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(stored.get("full_name").asText()).isEqualTo("Carlos A. Pérez");
        assertThat(stored.get("email").asText()).isEqualTo("nuevo@correo.com");
    }

    @Test
    void updateOfInactiveDriverKeepsItsStatus() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        UUID id = saveInactiveDriverCreatedAt(createdAt);

        ResponseEntity<String> response = put(id.toString(), """
                {"full_name":"Carlos A. Pérez","email":"carlos@correo.com"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode stored = MAPPER.readTree(client().get().uri("/{id}", id).retrieve().body(String.class));
        assertThat(stored.get("status").asText()).isEqualTo("INACTIVE_EXPIRED_DOCUMENTS");
        assertThat(stored.get("full_name").asText()).isEqualTo("Carlos A. Pérez");
    }

    @Test
    void updateUnknownDriverReturns404AndCreatesNothing() throws Exception {
        UUID id = UUID.randomUUID();

        ResponseEntity<String> response = put(id.toString(), """
                {"full_name":"Carlos Pérez","email":"carlos@correo.com"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertDriverNotFound(response, id.toString());
        assertThat(firestore.collection("drivers").document(id.toString()).get().get().exists()).isFalse();
    }

    @Test
    void updateWithSeveralInvalidFieldsReportsAllOfThem() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        ResponseEntity<String> response = put(id, """
                {"full_name":"Al","email":"sin-arroba","phone":"123"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldCodes(MAPPER.readTree(response.getBody()))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "full_name", "INVALID_LENGTH", "email", "INVALID_FORMAT", "phone", "INVALID_FORMAT"));
    }

    @Test
    void getDriverMakesNoOutboundCallsAndReturnsNoLicenseData() throws Exception {
        AtomicInteger outboundCalls = new AtomicInteger();
        HttpServer simulatedExternalService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        simulatedExternalService.createContext("/", exchange -> {
            outboundCalls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        simulatedExternalService.start();
        try {
            String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

            ResponseEntity<String> response = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(outboundCalls.get()).isZero();

            JsonNode body = MAPPER.readTree(response.getBody());
            List<String> fields = new ArrayList<>();
            body.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder(
                    "id", "full_name", "document_number", "email", "phone", "status", "created_at", "updated_at");
        } finally {
            simulatedExternalService.stop(0);
        }
    }

    @Test
    void twoConsecutiveGetsReturnSameBodyAndDoNotSave() throws Exception {
        String id = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();
        clearInvocations(driverRepository);

        ResponseEntity<String> first = client().get().uri("/{id}", id).retrieve().toEntity(String.class);
        ResponseEntity<String> second = client().get().uri("/{id}", id).retrieve().toEntity(String.class);

        assertThat(first.getBody()).isEqualTo(second.getBody());
        assertThat(MAPPER.readTree(second.getBody()).get("updated_at").asText())
                .isEqualTo(MAPPER.readTree(first.getBody()).get("updated_at").asText());
        verify(driverRepository, never()).save(any());
    }

    @Test
    void registerIgnoresStatusFieldAndReturnsActive() throws Exception {
        String body = """
                {"full_name":"Carlos Pérez","document_number":"%s","email":"carlos@correo.com",
                 "phone":"3001234567","status":"INACTIVE_EXPIRED_DOCUMENTS"}
                """.formatted(uniqueDocument());

        ResponseEntity<String> response = post(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(MAPPER.readTree(response.getBody()).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void registerResponseMatchesGetByIdOnSameFields() throws Exception {
        JsonNode registered = MAPPER.readTree(register(uniqueDocument()).getBody());

        ResponseEntity<String> fetched = client().get().uri("/{id}", registered.get("id").asText())
                .retrieve()
                .toEntity(String.class);
        JsonNode found = MAPPER.readTree(fetched.getBody());

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String field : List.of("id", "full_name", "document_number", "email", "phone", "status")) {
            assertThat(found.get(field)).isEqualTo(registered.get(field));
        }
    }

    @Test
    void registerTrimsDocumentNumberSpaces() throws Exception {
        String documentNumber = uniqueDocument();
        String body = """
                {"full_name":"Carlos Pérez","document_number":"  %s  ","email":"carlos@correo.com","phone":"3001234567"}
                """.formatted(documentNumber);

        ResponseEntity<String> response = post(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(MAPPER.readTree(response.getBody()).get("document_number").asText()).isEqualTo(documentNumber);
    }

    @Test
    void registerDocumentNumberWithLettersOrWrongLengthReturnsInvalidFormat() throws Exception {
        for (String documentNumber : List.of("abc123", "12345", "12345678901")) {
            ResponseEntity<String> response = register(documentNumber);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(fieldCodes(MAPPER.readTree(response.getBody())))
                    .containsEntry("document_number", "INVALID_FORMAT");
        }
    }

    @Test
    void registerPhoneWithWrongPrefixReturnsInvalidFormat() throws Exception {
        String body = """
                {"full_name":"Carlos Pérez","document_number":"%s","email":"carlos@correo.com","phone":"2123456789"}
                """.formatted(uniqueDocument());

        ResponseEntity<String> response = post(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldCodes(MAPPER.readTree(response.getBody()))).containsEntry("phone", "INVALID_FORMAT");
    }

    @Test
    void registerWithoutPhoneReturns201WithoutPhone() throws Exception {
        String body = """
                {"full_name":"Carlos Pérez","document_number":"%s","email":"carlos@correo.com"}
                """.formatted(uniqueDocument());

        ResponseEntity<String> response = post(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(MAPPER.readTree(response.getBody()).hasNonNull("phone")).isFalse();
    }

    @Test
    void registerDuplicateDocumentReturnsExactMessage() throws Exception {
        String documentNumber = uniqueDocument();
        register(documentNumber);

        ResponseEntity<String> response = register(documentNumber);

        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.get("code").asText()).isEqualTo("DUPLICATE_DRIVER");
        assertThat(body.get("message").asText())
                .isEqualTo("Ya existe un conductor con el documento de identidad " + documentNumber);
    }

    @Test
    void registerWritesDriverActivatedEventToOutbox() throws Exception {
        String driverId = MAPPER.readTree(register(uniqueDocument()).getBody()).get("id").asText();

        var events = firestore.collection("outbox_events").get().get().getDocuments().stream()
                .filter(event -> String.valueOf(event.getString("payload")).contains(driverId))
                .toList();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getString("event_type")).isEqualTo("driver_activated_event");
        assertThat(events.get(0).getString("status")).isIn("PENDING", "SENT");
        JsonNode data = MAPPER.readTree(events.get(0).getString("payload")).get("data");
        assertThat(data.get("driver_id").asText()).isEqualTo(driverId);
        assertThat(data.get("full_name").asText()).isEqualTo("Carlos Pérez");
    }

    @Test
    void registerMakesNoOutboundCallsToDocumentacion() throws Exception {
        AtomicInteger outboundCalls = new AtomicInteger();
        HttpServer simulatedExternalService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        simulatedExternalService.createContext("/", exchange -> {
            outboundCalls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        simulatedExternalService.start();
        try {
            ResponseEntity<String> response = register(uniqueDocument());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(outboundCalls.get()).isZero();
        } finally {
            simulatedExternalService.stop(0);
        }
    }

    @Test
    void registerSimultaneousSameDocumentReturnsOne201AndOne409() throws Exception {
        String documentNumber = uniqueDocument();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<String>>> requests = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                requests.add(executor.submit(() -> {
                    start.await();
                    return register(documentNumber);
                }));
            }
            start.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> request : requests) {
                responses.add(request.get(30, TimeUnit.SECONDS));
            }
            List<Integer> statuses = responses.stream().map(r -> r.getStatusCode().value()).toList();
            assertThat(statuses)
                    .as("respuestas: %s", responses.stream().map(ResponseEntity::getBody).toList())
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void registerStoresDocumentNumberInUniquenessCollectionWithDriverId() throws Exception {
        String documentNumber = uniqueDocument();
        String driverId = MAPPER.readTree(register(documentNumber).getBody()).get("id").asText();

        var reservation = firestore.collection("document_numbers").document(documentNumber).get().get();

        assertThat(reservation.exists()).isTrue();
        assertThat(reservation.getString("driver_id")).isEqualTo(driverId);
    }

    @Test
    void duplicateRegisterLeavesOnlyTheFirstDriverWithThatDocument() throws Exception {
        String documentNumber = uniqueDocument();
        String firstId = MAPPER.readTree(register(documentNumber).getBody()).get("id").asText();

        ResponseEntity<String> duplicate = register(documentNumber);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var drivers = firestore.collection("drivers")
                .whereEqualTo("document_number", documentNumber).get().get().getDocuments();
        assertThat(drivers).hasSize(1);
        assertThat(drivers.get(0).getId()).isEqualTo(firstId);
    }

    @Test
    void medicalReleaseReturns200WithUpdatedStatus() throws Exception {
        String documentNumber = uniqueDocument();
        String id = MAPPER.readTree(register(documentNumber).getBody()).get("id").asText();
        UUID driverId = UUID.fromString(id);

        Driver driver = driverRepository.findById(driverId).orElseThrow();
        driver.applyMedicalBlock(OffsetDateTime.now(ZoneOffset.ofHours(-5)));
        driverRepository.save(driver);

        ResponseEntity<String> response = medicalRelease(id, "{\"notes\":\"Alta otorgada\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("id").asText()).isEqualTo(id);
        assertThat(body.has("notes")).isFalse();
        assertThat(body.has("medical_release_notes")).isFalse();
    }

    @Test
    void medicalReleaseWithoutMedicalBlockReturns409StatusNotAllowed() throws Exception {
        String documentNumber = uniqueDocument();
        String id = MAPPER.readTree(register(documentNumber).getBody()).get("id").asText();

        ResponseEntity<String> response = medicalRelease(id, "{\"notes\":\"Alta\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("STATUS_NOT_ALLOWED");
    }

    @Test
    void medicalReleaseWithNonExistentIdReturns404NotFound() throws Exception {
        String missingId = UUID.randomUUID().toString();
        ResponseEntity<String> response = medicalRelease(missingId, "{\"notes\":\"Alta\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertDriverNotFound(response, missingId);
    }

    @Test
    void medicalReleaseWithInvalidNotesLengthReturns400InvalidData() throws Exception {
        String documentNumber = uniqueDocument();
        String id = MAPPER.readTree(register(documentNumber).getBody()).get("id").asText();

        String longNotes = "{\"notes\":\"" + "a".repeat(501) + "\"}";
        ResponseEntity<String> response = medicalRelease(id, longNotes);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_DATA");
    }

    @Test
    void medicalReleaseWithoutBodyReturns200() throws Exception {
        String documentNumber = uniqueDocument();
        String id = MAPPER.readTree(register(documentNumber).getBody()).get("id").asText();
        UUID driverId = UUID.fromString(id);

        Driver driver = driverRepository.findById(driverId).orElseThrow();
        driver.applyMedicalBlock(OffsetDateTime.now(ZoneOffset.ofHours(-5)));
        driverRepository.save(driver);

        ResponseEntity<String> response = medicalRelease(id, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
    }

    private ResponseEntity<String> medicalRelease(String id, String body) {
        var requestSpec = client().post().uri("/{id}/medical-release", id);
        if (body != null) {
            return requestSpec.contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
        }
        return requestSpec.retrieve().toEntity(String.class);
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
