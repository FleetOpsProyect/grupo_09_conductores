package co.edu.univalle.conductores.adapter.out.firestore;

/**
 * Names of the processed_events collection and its fields in Firestore.
 *
 * <p>Stores one document per consumed event, keyed by event_id, to make the consumer idempotent
 * and to record which event changed a driver's status.
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

    private ProcessedEventDocument() {
    }
}
