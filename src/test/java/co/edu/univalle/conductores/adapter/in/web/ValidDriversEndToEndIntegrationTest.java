package co.edu.univalle.conductores.adapter.in.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.config.FirestoreConfig;
import co.edu.univalle.conductores.config.FirestoreProperties;
import co.edu.univalle.conductores.domain.Driver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Prueba de integración punta a punta de HU-07 contra emulador aislado de Firestore y WireMock
 * (HU07-5). Usa su propio emulador para no alterar los conteos/páginas de otras pruebas.
 */
@DirtiesContext
class ValidDriversEndToEndIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROJECT_ID = "demo-conductores";
    private static final OffsetDateTime BASE_TIME =
            OffsetDateTime.of(2026, 10, 1, 8, 0, 0, 0, ZoneOffset.ofHours(-5));

    private static final GenericContainer<?> OWN_EMULATOR = newFirestoreEmulator();

    static {
        OWN_EMULATOR.start();
    }

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void documentacionProperties(DynamicPropertyRegistry registry) {
        registry.add("app.documentacion.url", wireMock::baseUrl);
    }

    @TestBean(name = "firestore", methodName = "isolatedFirestore")
    private Firestore firestore;

    @LocalServerPort
    private int port;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private CircuitBreaker documentacionCircuitBreaker;

    static Firestore isolatedFirestore() {
        return new FirestoreConfig().firestore(PROJECT_ID, ownEmulatorHost(),
                new FirestoreProperties(Duration.ofSeconds(2)));
    }

    @BeforeAll
    static void usesOwnEmulator(@Autowired Firestore firestore) {
        assertThat(firestore.getOptions().getEmulatorHost()).isEqualTo(ownEmulatorHost());
    }

    private static String ownEmulatorHost() {
        return OWN_EMULATOR.getHost() + ":" + OWN_EMULATOR.getMappedPort(FIRESTORE_PORT);
    }

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        documentacionCircuitBreaker.reset();
    }

    @Test
    void listsActiveDriversAcrossMultipleBatchesInCreatedAtOrder() throws Exception {
        // Sembramos 150 ACTIVE (para forzar 2 lotes: 100 + 50) y 1 MEDICAL_HOLD_INCIDENT.
        List<Driver> seededActive = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            Driver driver = Driver.restore(
                    UUID.randomUUID(),
                    uniqueDocument(),
                    "Conductor " + i,
                    "conductor" + i + "@correo.com",
                    "3001234567",
                    false,
                    false,
                    BASE_TIME.plusSeconds(i),
                    BASE_TIME.plusSeconds(i));
            driverRepository.save(driver);
            seededActive.add(driver);
        }
        Driver medicalHold = Driver.restore(
                UUID.randomUUID(),
                uniqueDocument(),
                "Bloqueado Medico",
                "medico@correo.com",
                "3001234567",
                false,
                true,
                BASE_TIME.plusSeconds(200),
                BASE_TIME.plusSeconds(200));
        driverRepository.save(medicalHold);

        // El primer lote devuelve el primero y el último; el segundo lote devuelve el último de todos.
        UUID batch1First = seededActive.get(0).getId();
        UUID batch1Last = seededActive.get(99).getId();
        UUID batch2Last = seededActive.get(149).getId();

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .inScenario("two-batches")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[{\"owner_id\":\"%s\"},{\"owner_id\":\"%s\"}]}"
                                .formatted(batch1First, batch1Last)))
                .willSetStateTo("SECOND_BATCH"));

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .inScenario("two-batches")
                .whenScenarioStateIs("SECOND_BATCH")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[{\"owner_id\":\"%s\"}]}".formatted(batch2Last))));

        ResponseEntity<String> response = client().get()
                .uri("?status=ACTIVE&valid_until=2099-12-31&size=999")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.path("total").asLong()).isEqualTo(3L);
        assertThat(body.path("truncated").asBoolean()).isFalse();

        List<String> returnedIds = new ArrayList<>();
        body.path("content").forEach(node -> returnedIds.add(node.path("id").asText()));
        assertThat(returnedIds).containsExactly(
                batch1First.toString(),
                batch1Last.toString(),
                batch2Last.toString());
        assertThat(returnedIds).doesNotContain(medicalHold.getId().toString());
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/documents/validity-check")));
    }

    @Test
    void returns503DependencyUnavailableWithoutPartialListWhenSecondBatchFails() throws Exception {
        for (int i = 0; i < 105; i++) {
            Driver driver = Driver.restore(
                    UUID.randomUUID(),
                    uniqueDocument(),
                    "Conductor Inactivo " + i,
                    "inactivo" + i + "@correo.com",
                    null,
                    true,
                    false,
                    BASE_TIME.plusSeconds(i),
                    BASE_TIME.plusSeconds(i));
            driverRepository.save(driver);
        }

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .inScenario("second-batch-fails")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}"))
                .willSetStateTo("FAIL_SECOND"));

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .inScenario("second-batch-fails")
                .whenScenarioStateIs("FAIL_SECOND")
                .willReturn(aResponse().withStatus(503)));

        ResponseEntity<String> response = client().get()
                .uri("?status=INACTIVE_EXPIRED_DOCUMENTS&valid_until=2099-12-31")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(body.path("message").asText())
                .isEqualTo("No fue posible validar la vigencia de las licencias, intente nuevamente");
        assertThat(body.has("content")).isFalse();
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/documents/validity-check")));
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port + "/drivers")
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    private static String uniqueDocument() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999));
    }
}
