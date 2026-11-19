package co.edu.univalle.conductores.application.port.out;

/**
 * Puerto para registrar métricas de publicación de eventos.
 */
public interface EventMetricsPort {

    /**
     * Registra un evento publicado correctamente.
     *
     * @param eventType tipo de evento del contrato
     */
    void recordPublished(String eventType);

    /**
     * Registra un intento fallido de publicación.
     *
     * @param eventType tipo de evento del contrato
     */
    void recordPublicationFailure(String eventType);

    /**
     * Actualiza el número actual de eventos pendientes en el outbox.
     *
     * @param count cantidad de eventos pendientes
     */
    void setPendingEvents(long count);
}
