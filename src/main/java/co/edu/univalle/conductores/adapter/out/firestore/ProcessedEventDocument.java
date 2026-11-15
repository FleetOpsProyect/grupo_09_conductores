package co.edu.univalle.conductores.adapter.out.firestore;

import com.google.cloud.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Names of the processed_events collection and its fields in Firestore.
 *
 * <p>Stores one document per consumed event, keyed by event_id, to make the consumer idempotent
 * and to record which event changed a driver's status. A Firestore TTL policy on {@link #EXPIRE_AT}
 * deletes each document {@link #RETENTION} after it was processed.
 */
final class ProcessedEventDocument {

    /** Collection name. The document id is the event_id. */
    static final String COLLECTION = "processed_events";

    /** String: id of the consumed event. */
    static final String EVENT_ID = "event_id";

    /** String: type of the consumed event, e.g. document_expired_event. */
    static final String EVENT_TYPE = "event_type";

    /** String: id of the affected driver. */
    static final String DRIVER_ID = "driver_id";

    /** Firestore Timestamp: when the event was processed. */
    static final String PROCESSED_AT = "processed_at";

    /** Firestore Timestamp: processed_at + {@link #RETENTION}. Field of the TTL policy. */
    static final String EXPIRE_AT = "expire_at";

    /** How long a processed event is kept to detect redeliveries. */
    static final Duration RETENTION = Duration.ofDays(30);

    private ProcessedEventDocument() {
    }

    /**
     * Builds the document stored for a processed event.
     *
     * @param eventId id of the consumed event
     * @param eventType type of the consumed event
     * @param driverId id of the affected driver
     * @param processedAt when the event was processed
     * @return document fields
     */
    static Map<String, Object> of(UUID eventId, String eventType, UUID driverId, Instant processedAt) {
        return Map.of(
                EVENT_ID, eventId.toString(),
                EVENT_TYPE, eventType,
                DRIVER_ID, driverId.toString(),
                PROCESSED_AT, toTimestamp(processedAt),
                EXPIRE_AT, toTimestamp(processedAt.plus(RETENTION)));
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }
}
