package co.edu.univalle.conductores.adapter.in.messaging;

import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.Command;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.DlqSenderPort;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor SQS que escucha eventos de fleetops_driver_queue.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class DriverEventListener {

    private static final Logger log = LoggerFactory.getLogger(DriverEventListener.class);

    private final ProcessDriverEvent processDriverEvent;
    private final ConsumerMetricsPort consumerMetrics;
    private final DlqSenderPort dlqSender;
    private final JsonMapper jsonMapper;

    /**
     * Construye el listener de eventos de conductores.
     *
     * @param processDriverEvent caso de uso para procesar eventos
     * @param consumerMetrics    puerto de métricas del consumidor
     * @param dlqSender          puerto para enviar mensajes a la DLQ
     * @param jsonMapper         mapper para deserializar los mensajes JSON
     */
    public DriverEventListener(
            ProcessDriverEvent processDriverEvent,
            ConsumerMetricsPort consumerMetrics,
            DlqSenderPort dlqSender,
            JsonMapper jsonMapper) {
        this.processDriverEvent = processDriverEvent;
        this.consumerMetrics = consumerMetrics;
        this.dlqSender = dlqSender;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Escucha mensajes de la cola de eventos de conductores.
     *
     * @param payload      cuerpo del mensaje
     * @param receiveCount número aproximado de recepciones para detectar
     *                     max_retries
     */
    @SqsListener("${app.messaging.driver-queue:fleetops_driver_queue}")
    public void onMessage(
            String payload,
            @Header(name = "ApproximateReceiveCount", required = false) String receiveCount) {
        String eventType = "unknown";

        try {
            JsonNode root = parseJson(payload);
            if (root.has("Type") && "Notification".equals(root.get("Type").asString()) && root.has("Message")) {
                root = parseJson(root.get("Message").asString());
            }

            eventType = extractText(root, "event_type", "eventType");
            UUID eventId = extractUuid(root, "event_id", "eventId");
            JsonNode dataNode = root.get("data");
            UUID driverId = dataNode != null ? extractUuid(dataNode, "driver_id", "driverId") : null;

            if (eventId == null || driverId == null || eventType == null || "unknown".equals(eventType)) {
                handleInvalidMessage(payload, eventType, "Faltan campos obligatorios en el evento");
                return;
            }

            OffsetDateTime occurredAt = extractOccurredAt(root);
            UUID correlationId = extractUuid(root, "correlation_id", "correlationId");

            Command command = new Command(eventId, eventType, driverId, occurredAt, correlationId);
            processDriverEvent.process(command);

        } catch (DriverNotFoundException ex) {
            log.warn("Conductor no encontrado para el evento {}: {}", eventType, ex.getMessage());
            consumerMetrics.recordDlq(eventType, "driver_not_found");
            dlqSender.sendToDlq(payload, eventType, "driver_not_found");

        } catch (IllegalArgumentException ex) {
            handleInvalidMessage(payload, eventType, ex.getMessage());

        } catch (Exception ex) {
            if (isMaxRetries(receiveCount)) {
                log.error("Evento {} alcanzó el máximo de reintentos: {}", eventType, ex.getMessage());
                consumerMetrics.recordDlq(eventType, "max_retries");
            }
            throw ex;
        }
    }

    private JsonNode parseJson(String payload) {
        try {
            return jsonMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Mensaje con formato JSON inválido", ex);
        }
    }

    private String extractText(JsonNode node, String snakeCase, String camelCase) {
        if (node.has(snakeCase) && !node.get(snakeCase).isNull()) {
            return node.get(snakeCase).asString();
        }
        if (node.has(camelCase) && !node.get(camelCase).isNull()) {
            return node.get(camelCase).asString();
        }
        return "unknown";
    }

    private UUID extractUuid(JsonNode node, String snakeCase, String camelCase) {
        String text = extractText(node, snakeCase, camelCase);
        if ("unknown".equals(text) || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Identificador UUID inválido: " + text, ex);
        }
    }

    private OffsetDateTime extractOccurredAt(JsonNode node) {
        String text = extractText(node, "occurred_at", "occurredAt");
        if (!"unknown".equals(text) && !text.isBlank()) {
            try {
                return OffsetDateTime.parse(text.trim());
            } catch (Exception ex) {
                log.debug("No se pudo parsear occurred_at: {}, usando hora actual", text);
            }
        }
        return OffsetDateTime.now();
    }

    private void handleInvalidMessage(String payload, String eventType, String reason) {
        log.warn("Mensaje descartado hacia DLQ ({}): {}", reason, payload);
        consumerMetrics.recordDlq(eventType, "invalid_message");
        dlqSender.sendToDlq(payload, eventType, "invalid_message");
    }

    private boolean isMaxRetries(String receiveCount) {
        if (receiveCount == null) {
            return false;
        }
        try {
            return Integer.parseInt(receiveCount) >= 3;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
