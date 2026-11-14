package co.edu.univalle.conductores.application.port.out;

import java.util.UUID;

/**
 * Puerto de salida para verificar y registrar la idempotencia de eventos consumidos.
 */
public interface ProcessedEventRepositoryPort {

    /**
     * Comprueba si un evento ya fue procesado con anterioridad.
     *
     * @param eventId identificador único del evento
     * @return true si ya existe en la colección de eventos procesados
     */
    boolean isProcessed(UUID eventId);

    /**
     * Guarda el registro de un evento consumido exitosamente.
     *
     * @param eventId identificador único del evento
     * @param eventType tipo de evento
     * @param driverId identificador del conductor afectado
     */
    void saveProcessed(UUID eventId, String eventType, UUID driverId);
}

