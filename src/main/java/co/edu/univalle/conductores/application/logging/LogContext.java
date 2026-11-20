package co.edu.univalle.conductores.application.logging;

import java.util.Optional;
import org.slf4j.MDC;

/**
 * Claves del contexto de log (MDC) que salen como campos del JSON estructurado (TEC_03).
 *
 * <p>Cada petición HTTP lleva {@link #CORRELATION_ID}, {@link #HTTP_METHOD} y {@link #HTTP_PATH};
 * cada evento consumido o publicado lleva {@link #CORRELATION_ID}, {@link #EVENT_ID} y
 * {@link #EVENT_TYPE}.
 */
public final class LogContext {

    /** Encabezado HTTP del que se toma y al que se envía el correlation_id. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** Identificador de correlación de la operación. */
    public static final String CORRELATION_ID = "correlation_id";

    /** Identificador del evento consumido o publicado. */
    public static final String EVENT_ID = "event_id";

    /** Tipo del evento consumido o publicado. */
    public static final String EVENT_TYPE = "event_type";

    /** Método de la petición HTTP. */
    public static final String HTTP_METHOD = "http_method";

    /** Ruta de la petición HTTP, sin query string. */
    public static final String HTTP_PATH = "http_path";

    private LogContext() {
    }

    /**
     * Devuelve el correlation_id de la operación en curso, si hay uno en el contexto de log.
     *
     * @return correlation_id actual
     */
    public static Optional<String> currentCorrelationId() {
        return Optional.ofNullable(MDC.get(CORRELATION_ID)).filter(value -> !value.isBlank());
    }
}
