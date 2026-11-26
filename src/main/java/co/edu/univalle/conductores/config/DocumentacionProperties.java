package co.edu.univalle.conductores.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del cliente hacia el servicio de Documentación (HU-07), enlazada desde
 * {@code app.documentacion.*}.
 *
 * @param url            URL base del servicio de Documentación ({@code DOCUMENTACION_URL})
 * @param timeoutMs      tiempo máximo de espera en milisegundos ({@code DOCUMENTACION_TIMEOUT_MS},
 *                       por defecto 2000)
 * @param circuitBreaker configuración del Circuit Breaker de Resilience4j
 */
@ConfigurationProperties("app.documentacion")
public record DocumentacionProperties(String url, long timeoutMs, CircuitBreakerSettings circuitBreaker) {

    /**
     * Valida que el timeout configurado sea positivo y aplica valores por defecto al circuit breaker
     * si no se especifican.
     */
    public DocumentacionProperties {
        if (timeoutMs < 1) {
            throw new IllegalArgumentException(
                    "app.documentacion.timeout-ms (DOCUMENTACION_TIMEOUT_MS) must be at least 1 ms, got: "
                            + timeoutMs);
        }
        if (circuitBreaker == null) {
            circuitBreaker = new CircuitBreakerSettings(5, 30_000L);
        }
    }

    /**
     * Parámetros del Circuit Breaker hacia Documentación.
     *
     * @param maxConsecutiveFailures fallos consecutivos para abrir el circuito
     *                               ({@code CIRCUIT_BREAKER_MAX_CONSECUTIVE_FAILURES}, por defecto 5)
     * @param waitDurationMs         tiempo de espera en estado abierto en ms
     *                               ({@code CIRCUIT_BREAKER_WAIT_DURATION_MS}, por defecto 30000)
     */
    public record CircuitBreakerSettings(int maxConsecutiveFailures, long waitDurationMs) {

        /**
         * Valida los parámetros del circuit breaker.
         */
        public CircuitBreakerSettings {
            if (maxConsecutiveFailures < 1) {
                throw new IllegalArgumentException(
                        "CIRCUIT_BREAKER_MAX_CONSECUTIVE_FAILURES must be at least 1, got: "
                                + maxConsecutiveFailures);
            }
            if (waitDurationMs < 1) {
                throw new IllegalArgumentException(
                        "CIRCUIT_BREAKER_WAIT_DURATION_MS must be at least 1 ms, got: " + waitDurationMs);
            }
        }
    }
}
