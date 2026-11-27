package co.edu.univalle.conductores.adapter.out.documentacion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import co.edu.univalle.conductores.config.DocumentacionClientConfig;
import co.edu.univalle.conductores.config.DocumentacionProperties;
import co.edu.univalle.conductores.config.DocumentacionProperties.CircuitBreakerSettings;
import co.edu.univalle.conductores.domain.DocumentationUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Pruebas del Circuit Breaker Resilience4j alrededor de {@link HttpDocumentValidityAdapter} con
 * WireMock (HU07-4).
 */
class DocumentacionCircuitBreakerWireMockTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 12, 31);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    void opensAfter5ConsecutiveFailuresAndShortCircuitsSixthCallWithoutHittingWireMock() {
        DocumentacionClientConfig config = new DocumentacionClientConfig();
        DocumentacionProperties props = new DocumentacionProperties(
                wireMock.baseUrl(), 1000, new CircuitBreakerSettings(5, 30_000L));
        CircuitBreaker cb = config.documentacionCircuitBreaker(props);
        DocumentValidityPort adapter = config.documentValidityPort(
                props, new CorrelationIdClientInterceptor(), cb);

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .willReturn(aResponse().withStatus(503)));

        List<UUID> ids = List.of(UUID.randomUUID());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> adapter.findDriversWithValidLicense(ids, VALID_UNTIL))
                    .isInstanceOf(DocumentationUnavailableException.class);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        wireMock.verify(5, getRequestedFor(urlPathEqualTo("/documents/validity-check")));

        // La 6.ª llamada falla de inmediato por circuito abierto sin llegar a WireMock.
        assertThatThrownBy(() -> adapter.findDriversWithValidLicense(ids, VALID_UNTIL))
                .isInstanceOf(DocumentationUnavailableException.class)
                .hasMessageContaining("Circuito abierto");
        wireMock.verify(5, getRequestedFor(urlPathEqualTo("/documents/validity-check")));
    }

    @Test
    void transitionsToHalfOpenAfterWaitDurationAndClosesOnRecovery() throws Exception {
        DocumentacionClientConfig config = new DocumentacionClientConfig();
        DocumentacionProperties props = new DocumentacionProperties(
                wireMock.baseUrl(), 1000, new CircuitBreakerSettings(5, 150L));
        CircuitBreaker cb = config.documentacionCircuitBreaker(props);
        DocumentValidityPort adapter = config.documentValidityPort(
                props, new CorrelationIdClientInterceptor(), cb);

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .willReturn(aResponse().withStatus(500)));

        UUID driverId = UUID.randomUUID();
        List<UUID> ids = List.of(driverId);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> adapter.findDriversWithValidLicense(ids, VALID_UNTIL))
                    .isInstanceOf(DocumentationUnavailableException.class);
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(200);

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": [{\"owner_id\": \"%s\"}]}".formatted(driverId))));

        Set<UUID> recovered = adapter.findDriversWithValidLicense(ids, VALID_UNTIL);

        assertThat(recovered).containsExactly(driverId);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        wireMock.verify(6, getRequestedFor(urlPathEqualTo("/documents/validity-check")));
    }
}
