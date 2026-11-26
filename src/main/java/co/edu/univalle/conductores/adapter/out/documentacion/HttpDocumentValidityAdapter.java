package co.edu.univalle.conductores.adapter.out.documentacion;

import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import co.edu.univalle.conductores.domain.DocumentationUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador de salida HTTP hacia el servicio de Documentación (HU-07).
 */
public class HttpDocumentValidityAdapter implements DocumentValidityPort {

    private final RestClient restClient;
    private final String baseUrl;

    /**
     * Crea el adaptador HTTP.
     *
     * @param restClient cliente REST ya configurado con timeouts e interceptor de correlation_id
     * @param baseUrl    URL base del servicio de Documentación (puede estar vacía si no se usa)
     */
    public HttpDocumentValidityAdapter(RestClient restClient, String baseUrl) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.baseUrl = baseUrl;
    }

    @Override
    public Set<UUID> findDriversWithValidLicense(List<UUID> driverIds, LocalDate validUntil) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new DocumentationUnavailableException("DOCUMENTACION_URL no está configurada");
        }
        String joinedIds = driverIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
        try {
            ValidityCheckResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/documents/validity-check")
                            .queryParam("owner_type", "DRIVER")
                            .queryParam("valid_until", validUntil.toString())
                            .queryParam("owner_ids", joinedIds)
                            .queryParam("size", MAX_BATCH_SIZE)
                            .build())
                    .retrieve()
                    .body(ValidityCheckResponse.class);
            if (response == null || response.data() == null) {
                return Set.of();
            }
            return response.data().stream()
                    .map(ValidityItem::ownerId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RestClientException ex) {
            throw new DocumentationUnavailableException(
                    "Error al consultar vigencia de licencias en Documentación", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ValidityCheckResponse(List<ValidityItem> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ValidityItem(@JsonProperty("owner_id") UUID ownerId) {
    }
}
