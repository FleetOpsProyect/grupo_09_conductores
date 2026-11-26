package co.edu.univalle.conductores.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del cliente hacia el servicio de Documentación (HU-07), enlazada desde
 * {@code app.documentacion.*}.
 *
 * @param url       URL base del servicio de Documentación ({@code DOCUMENTACION_URL})
 * @param timeoutMs tiempo máximo de espera en milisegundos ({@code DOCUMENTACION_TIMEOUT_MS}, por
 *                  defecto 2000)
 */
@ConfigurationProperties("app.documentacion")
public record DocumentacionProperties(String url, long timeoutMs) {

    /**
     * Valida que el timeout configurado sea positivo.
     */
    public DocumentacionProperties {
        if (timeoutMs < 1) {
            throw new IllegalArgumentException(
                    "app.documentacion.timeout-ms (DOCUMENTACION_TIMEOUT_MS) must be at least 1 ms, got: "
                            + timeoutMs);
        }
    }
}
