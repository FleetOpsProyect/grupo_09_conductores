package co.edu.univalle.conductores.adapter.out.documentacion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.conductores.application.logging.LogContext;
import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import co.edu.univalle.conductores.config.DocumentacionClientConfig;
import co.edu.univalle.conductores.config.DocumentacionProperties;
import co.edu.univalle.conductores.domain.DocumentationUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

/**
 * Pruebas de integración del adaptador HTTP hacia Documentación con WireMock (HU07-3).
 */
class HttpDocumentValidityAdapterWireMockTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 12, 31);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private DocumentValidityPort adapterWithTimeout(long timeoutMs) {
        return new DocumentacionClientConfig().documentValidityPort(
                new DocumentacionProperties(wireMock.baseUrl(), timeoutMs),
                new CorrelationIdClientInterceptor());
    }

    @Test
    void returnsValidOwnerIdsAndSendsQueryParamsAndCorrelationId() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        MDC.put(LogContext.CORRELATION_ID, correlationId);

        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {"owner_id": "%s", "status": "VALID"}
                                  ]
                                }
                                """.formatted(first))));

        Set<UUID> result = adapterWithTimeout(2000)
                .findDriversWithValidLicense(List.of(first, second), VALID_UNTIL);

        assertThat(result).containsExactly(first);
        wireMock.verify(getRequestedFor(urlPathEqualTo("/documents/validity-check"))
                .withQueryParam("owner_type", equalTo("DRIVER"))
                .withQueryParam("valid_until", equalTo("2026-12-31"))
                .withQueryParam("owner_ids", equalTo(first + "," + second))
                .withQueryParam("size", equalTo("100"))
                .withHeader(LogContext.CORRELATION_ID_HEADER, equalTo(correlationId)));
    }

    @Test
    void emptyDataReturnsEmptySet() {
        UUID driverId = UUID.randomUUID();
        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": []}")));

        Set<UUID> result = adapterWithTimeout(2000)
                .findDriversWithValidLicense(List.of(driverId), VALID_UNTIL);

        assertThat(result).isEmpty();
    }

    @Test
    void timeoutThrowsDocumentationUnavailableException() {
        UUID driverId = UUID.randomUUID();
        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": []}")));

        DocumentValidityPort adapter = adapterWithTimeout(150);

        assertThatThrownBy(() -> adapter.findDriversWithValidLicense(List.of(driverId), VALID_UNTIL))
                .isInstanceOf(DocumentationUnavailableException.class);
    }

    @Test
    void server5xxThrowsDocumentationUnavailableException() {
        UUID driverId = UUID.randomUUID();
        wireMock.stubFor(get(urlPathEqualTo("/documents/validity-check"))
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

        DocumentValidityPort adapter = adapterWithTimeout(2000);

        assertThatThrownBy(() -> adapter.findDriversWithValidLicense(List.of(driverId), VALID_UNTIL))
                .isInstanceOf(DocumentationUnavailableException.class);
    }

    @Test
    void blankUrlThrowsDocumentationUnavailableException() {
        DocumentValidityPort adapter = new DocumentacionClientConfig().documentValidityPort(
                new DocumentacionProperties("", 2000),
                new CorrelationIdClientInterceptor());

        assertThatThrownBy(() -> adapter.findDriversWithValidLicense(List.of(UUID.randomUUID()), VALID_UNTIL))
                .isInstanceOf(DocumentationUnavailableException.class);
    }
}
