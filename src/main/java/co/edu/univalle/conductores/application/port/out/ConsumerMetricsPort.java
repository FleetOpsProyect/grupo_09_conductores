package co.edu.univalle.conductores.application.port.out;

/**
 * Puerto para registrar métricas de consumo de eventos de conductores.
 */
public interface ConsumerMetricsPort {

    /**
     * Registra un evento consumido con su resultado.
     *
     * @param eventType tipo de evento (ej. document_expired_event)
     * @param result resultado del procesamiento: "processed" o "duplicate"
     */
    void recordConsumed(String eventType, String result);

    /**
     * Registra un evento descartado o enviado a la DLQ.
     *
     * @param eventType tipo de evento
     * @param reason causa del envío: "invalid_message", "driver_not_found" o "max_retries"
     */
    void recordDlq(String eventType, String reason);
}

