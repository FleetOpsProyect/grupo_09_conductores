package co.edu.univalle.conductores.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TEC_01: the Firestore data model and its composite indexes are versioned in the repository.
 */
class FirestoreIndexesTest {

    private static final Path INDEXES_FILE = Path.of("firestore.indexes.json");

    private static JsonNode indexes;

    @BeforeAll
    static void readIndexesFile() throws IOException {
        indexes = new ObjectMapper().readTree(INDEXES_FILE.toFile()).get("indexes");
    }

    @Test
    void driversIndexOnStatusAndCreatedAtExists() {
        assertThat(fieldsOfIndex(DriverDocument.COLLECTION))
                .containsExactly("status ASCENDING", "created_at ASCENDING");
    }

    @Test
    void outboxIndexOnStatusAndCreatedAtExists() {
        assertThat(fieldsOfIndex(OutboxEventDocument.COLLECTION)).containsExactly(
                OutboxEventDocument.STATUS + " ASCENDING",
                OutboxEventDocument.CREATED_AT + " ASCENDING");
    }

    @Test
    void collectionNamesMatchTec01() {
        assertThat(DriverDocument.COLLECTION).isEqualTo("drivers");
        assertThat(ProcessedEventDocument.COLLECTION).isEqualTo("processed_events");
        assertThat(OutboxEventDocument.COLLECTION).isEqualTo("outbox_events");
    }

    @Test
    void processedEventFieldsMatchDas() {
        assertThat(List.of(
                ProcessedEventDocument.EVENT_ID,
                ProcessedEventDocument.EVENT_TYPE,
                ProcessedEventDocument.DRIVER_ID,
                ProcessedEventDocument.PROCESSED_AT))
                .containsExactly("event_id", "event_type", "driver_id", "processed_at");
    }

    @Test
    void outboxEventFieldsAndStatusesMatchTec05() {
        assertThat(List.of(
                OutboxEventDocument.EVENT_ID,
                OutboxEventDocument.EVENT_TYPE,
                OutboxEventDocument.PAYLOAD,
                OutboxEventDocument.STATUS,
                OutboxEventDocument.CREATED_AT,
                OutboxEventDocument.ATTEMPTS))
                .containsExactly("event_id", "event_type", "payload", "status", "created_at", "attempts");
        assertThat(OutboxEventDocument.STATUS_PENDING).isEqualTo("PENDING");
        assertThat(OutboxEventDocument.STATUS_SENT).isEqualTo("SENT");
    }

    private static List<String> fieldsOfIndex(String collection) {
        JsonNode index = StreamSupport.stream(indexes.spliterator(), false)
                .filter(node -> collection.equals(node.path("collectionGroup").asText()))
                .filter(node -> "COLLECTION".equals(node.path("queryScope").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing index for " + collection));
        return StreamSupport.stream(index.get("fields").spliterator(), false)
                .map(field -> field.path("fieldPath").asText() + " " + field.path("order").asText())
                .toList();
    }
}
