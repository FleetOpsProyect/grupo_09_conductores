package co.edu.univalle.conductores.config;

import co.edu.univalle.conductores.adapter.out.documentacion.CorrelationIdClientInterceptor;
import co.edu.univalle.conductores.adapter.out.documentacion.HttpDocumentValidityAdapter;
import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configura el cliente HTTP hacia el servicio de Documentación y su Circuit Breaker (HU-07).
 */
@Configuration
@EnableConfigurationProperties(DocumentacionProperties.class)
public class DocumentacionClientConfig {

    /**
     * Crea el Circuit Breaker de Resilience4j para el servicio de Documentación.
     *
     * @param properties propiedades con fallos consecutivos y duración de espera
     * @return instancia de {@link CircuitBreaker}
     */
    @Bean
    public CircuitBreaker documentacionCircuitBreaker(DocumentacionProperties properties) {
        DocumentacionProperties.CircuitBreakerSettings cb = properties.circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.maxConsecutiveFailures())
                .minimumNumberOfCalls(cb.maxConsecutiveFailures())
                .failureRateThreshold(100f)
                .waitDurationInOpenState(Duration.ofMillis(cb.waitDurationMs()))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        return CircuitBreakerRegistry.of(config).circuitBreaker("documentacion");
    }

    /**
     * Crea el puerto de consulta de vigencia de licencias respaldado por {@link RestClient} y
     * protegido por {@link CircuitBreaker}.
     *
     * @param properties              configuración {@code DOCUMENTACION_URL} y {@code DOCUMENTACION_TIMEOUT_MS}
     * @param correlationInterceptor  interceptor que propaga {@code X-Correlation-Id}
     * @param circuitBreaker          circuit breaker configurado para Documentación
     * @return implementación HTTP de {@link DocumentValidityPort}
     */
    @Bean
    public DocumentValidityPort documentValidityPort(
            DocumentacionProperties properties,
            CorrelationIdClientInterceptor correlationInterceptor,
            CircuitBreaker documentacionCircuitBreaker) {
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
        return new HttpDocumentValidityAdapter(
                builder.build(), properties.url(), documentacionCircuitBreaker);
    }
}
