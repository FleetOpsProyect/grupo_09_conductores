package co.edu.univalle.conductores.application.port.out;

import co.edu.univalle.conductores.application.event.OutboxEvent;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida para consultar y actualizar eventos del outbox.
 */
public interface OutboxRepositoryPort {

    /**
     * Counts all events that are still pending publication.
     *
     * @return number of pending events
     */
    long countPending();

    /**
     * Busca eventos pendientes, ordenados por fecha de creación.
     *
     * @param limit máximo de eventos a devolver
     * @return eventos pendientes
     */
    List<OutboxEvent> findPending(int limit);

    /**
     * Marca como enviado un evento confirmado por SNS.
     *
     * @param eventId identificador del evento
     */
    void markAsSent(UUID eventId);

    /**
     * Registra un intento fallido y mantiene el evento pendiente.
     *
     * @param eventId identificador del evento
     */
    void recordPublishFailure(UUID eventId);
}