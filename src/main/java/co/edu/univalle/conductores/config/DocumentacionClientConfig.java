package co.edu.univalle.conductores.config;

import co.edu.univalle.conductores.adapter.out.documentacion.CorrelationIdClientInterceptor;
import co.edu.univalle.conductores.adapter.out.documentacion.HttpDocumentValidityAdapter;
import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configura el cliente HTTP hacia el servicio de Documentación (HU-07).
 */
@Configuration
@EnableConfigurationProperties(DocumentacionProperties.class)
public class DocumentacionClientConfig {

    /**
     * Crea el puerto de consulta de vigencia de licencias respaldado por {@link RestClient}.
     *
     * @param properties              configuración {@code DOCUMENTACION_URL} y {@code DOCUMENTACION_TIMEOUT_MS}
     * @param correlationInterceptor  interceptor que propaga {@code X-Correlation-Id}
     * @return implementación HTTP de {@link DocumentValidityPort}
     */
    @Bean
    public DocumentValidityPort documentValidityPort(
            DocumentacionProperties properties,
            CorrelationIdClientInterceptor correlationInterceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(correlationInterceptor);
        if (properties.url() != null && !properties.url().isBlank()) {
            builder.baseUrl(properties.url().trim());
        }
        return new HttpDocumentValidityAdapter(builder.build(), properties.url());
    }
}
