package co.edu.univalle.conductores.application.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Contrato común del envelope para eventos de FleetOps.
 *
 * @param eventId identificador único del evento
 * @param eventType nombre del evento publicado
 * @param occurredAt fecha y hora del evento
 * @param source slug del microservicio que publica
 * @param correlationId identificador de correlación de la operación
 * @param data payload específico del evento
 * @param <T> tipo del payload
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        String source,
        UUID correlationId,
        T data) {
}
