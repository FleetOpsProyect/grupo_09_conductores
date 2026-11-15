package co.edu.univalle.conductores.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Caso de uso para procesar eventos asíncronos que afectan a conductores.
 */
public interface ProcessDriverEvent {

    /**
     * Resultado del procesamiento de un evento.
     */
    enum EventProcessingResult {
        PROCESSED,
        DUPLICATE
    }

    /**
     * Comando con los datos del evento consumido.
     *
     * @param eventId identificador del evento
     * @param eventType tipo de evento (document_expired_event, document_renewed_event, severe_incident_reported_event)
     * @param driverId identificador del conductor
     * @param occurredAt fecha y hora de ocurrencia
     * @param correlationId identificador de correlación
     */
    record Command(
            UUID eventId,
            String eventType,
            UUID driverId,
            OffsetDateTime occurredAt,
            UUID correlationId) {
    }

    /**
     * Procesa el evento de acuerdo a las reglas de negocio.
     *
     * @param command comando con la información del evento
     * @return resultado indicando si fue procesado o detectado como duplicado
     */
    EventProcessingResult process(Command command);
}

