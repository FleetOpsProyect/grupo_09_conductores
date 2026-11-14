package co.edu.univalle.conductores.adapter.out.firestore;

import co.edu.univalle.conductores.application.event.OutboxEvent;
import com.google.cloud.Timestamp;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Names of the outbox_events collection, its fields and status values in Firestore.
 *
 * <p>Stores the outgoing events in the same transaction as the driver change, so they can be
 * published later. Pending events are read with the composite index status + created_at.
 */
final class OutboxEventDocument {

    /** Collection name. The document id is the event_id. */
    static final String COLLECTION = "outbox_events";

    /** String: id of the outgoing event. */
    static final String EVENT_ID = "event_id";

    /** String: type of the outgoing event, e.g. driver_activated_event. */
    static final String EVENT_TYPE = "event_type";

    /**
     * String: serialized JSON of the full envelope
     * (event_id, event_type, occurred_at, source, correlation_id, data). Not stored as a map.
     */
    static final String PAYLOAD = "payload";

    /** String: publication status, {@link #STATUS_PENDING} or {@link #STATUS_SENT}. */
    static final String STATUS = "status";

    /** Firestore Timestamp: when the event was stored. Defines the publication order. */
    static final String CREATED_AT = "created_at";

    /** Number: failed publication attempts. */
    static final String ATTEMPTS = "attempts";

    /** Status of an event that has not been published yet. */
    static final String STATUS_PENDING = "PENDING";

    /** Status of an event already published to the broker. */
    static final String STATUS_SENT = "SENT";

    /**
     * Convierte un evento del outbox al formato almacenado en Firestore.
     *
     * @param outboxEvent evento que se guardará
     * @param objectMapper mapper configurado por Spring
     * @return campos del documento
     */
    static Map<String, Object> fromDomain(OutboxEvent outboxEvent, JsonMapper jsonMapper) {
        String payload;
        try {
            payload = jsonMapper.writeValueAsString(outboxEvent.envelope());
        } catch (JacksonException ex) {
            throw new IllegalStateException("No se pudo serializar el evento del outbox", ex);
        }

        return Map.of(
                EVENT_ID, outboxEvent.envelope().eventId().toString(),
                EVENT_TYPE, outboxEvent.envelope().eventType(),
                PAYLOAD, payload,
                STATUS, outboxEvent.status().name(),
                CREATED_AT, Timestamp.ofTimeSecondsAndNanos(
                        outboxEvent.envelope().occurredAt().toEpochSecond(),
                        outboxEvent.envelope().occurredAt().getNano()),
                ATTEMPTS, outboxEvent.attempts());
    }

    private OutboxEventDocument() {
    }
}
