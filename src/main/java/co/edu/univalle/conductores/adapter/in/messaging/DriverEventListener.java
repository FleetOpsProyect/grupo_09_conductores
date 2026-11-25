package co.edu.univalle.conductores.adapter.in.messaging;

import co.edu.univalle.conductores.application.logging.LogContext;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.Command;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.EventProcessingResult;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.DlqSenderPort;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor SQS que escucha eventos de fleetops_driver_queue.
 *
 * <p>Valida el sobre estándar {@code {event_id, event_type, occurred_at, source, correlation_id, data}}
 * y los campos de {@code data} que exige cada tipo de evento: {@code document_expired_event} y
 * {@code document_renewed_event} traen {@code document_id, document_type, owner_type, owner_id}; solo el
 * primero exige {@code expiration_date}. Ambos afectan solo a conductores ({@code owner_type = DRIVER}),
 * y los demás tipos traen {@code driver_id}. Un mensaje inválido o con tipo
 * desconocido va a la DLQ sin reintentos, con log ERROR. Si falla la persistencia, la excepción se relanza
 * para que SQS reintente; al tercer intento la redrive policy lo mueve a la DLQ.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class DriverEventListener {

    /** Igual al maxReceiveCount de la redrive policy de fleetops_driver_queue. */
    static final int MAX_RECEIVES = 3;

    private static final Logger log = LoggerFactory.getLogger(DriverEventListener.class);
    private static final String UNKNOWN = "unknown";
    private static final String DRIVER_OWNER = "DRIVER";

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
     * @param receiveCount número aproximado de recepciones que informa SQS, para detectar max_retries
     */
    @SqsListener("${app.messaging.driver-queue:fleetops_driver_queue}")
    public void onMessage(
            String payload,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT, required = false)
            String receiveCount) {
        String eventType = UNKNOWN;
        String eventId = UNKNOWN;
        Command command = null;

        try {
            JsonNode root = unwrapSnsNotification(parseJson(payload));
            eventType = textOrUnknown(root, "event_type");
            eventId = textOrUnknown(root, "event_id");
            MDC.put(LogContext.EVENT_ID, eventId);
            MDC.put(LogContext.EVENT_TYPE, eventType);
            MDC.put(LogContext.CORRELATION_ID, textOrUnknown(root, "correlation_id"));

            command = toCommand(root);
            if (command == null) {
                log.debug("Evento {} ({}) de un documento que no es de conductor: se confirma sin cambios",
                        eventId, eventType);
                consumerMetrics.recordConsumed(eventType, "ignored");
                return;
            }
            EventProcessingResult result = processDriverEvent.process(command);
            if (result == EventProcessingResult.PROCESSED
                    && ProcessDriverEvent.DOCUMENT_EXPIRED_EVENT.equals(eventType)) {
                log.info("Bloqueo por documento vencido aplicado: owner_id={}, document_id={}",
                        command.driverId(), textOrUnknown(root.get("data"), "document_id"));
            }

        } catch (DriverNotFoundException ex) {
            log.warn("Conductor no encontrado, el evento pasa a la DLQ: {}={}, event_id={}, event_type={}",
                    driverIdField(eventType), command == null ? UNKNOWN : command.driverId(), eventId, eventType);
            consumerMetrics.recordDlq(eventType, "driver_not_found");
            dlqSender.sendToDlq(payload, eventType, "driver_not_found");

        } catch (IllegalArgumentException ex) {
            log.error("Mensaje inválido enviado a la DLQ: event_id={}, event_type={}, motivo={}",
                    eventId, eventType, ex.getMessage());
            consumerMetrics.recordDlq(eventType, "invalid_message");
            dlqSender.sendToDlq(payload, eventType, "invalid_message");

        } catch (RuntimeException ex) {
            if (isLastAttempt(receiveCount)) {
                log.error("Evento {} ({}) alcanzó el máximo de {} intentos y pasará a la DLQ: {}",
                        eventId, eventType, MAX_RECEIVES, ex.getMessage());
                consumerMetrics.recordDlq(eventType, "max_retries");
            }
            throw ex;
        } finally {
            MDC.remove(LogContext.EVENT_ID);
            MDC.remove(LogContext.EVENT_TYPE);
            MDC.remove(LogContext.CORRELATION_ID);
        }
    }

    /** Name of the field the driver id came from, to log it as the publisher sent it. */
    private static String driverIdField(String eventType) {
        boolean document = ProcessDriverEvent.DOCUMENT_EXPIRED_EVENT.equals(eventType)
                || ProcessDriverEvent.DOCUMENT_RENEWED_EVENT.equals(eventType);
        return document ? "owner_id" : "driver_id";
    }

    /**
     * Builds the command from the envelope, or returns null when the event does not concern a driver (a
     * document whose owner is not a driver, HU-04 c3).
     */
    private Command toCommand(JsonNode root) {
        UUID eventId = requiredUuid(root, "event_id");
        String eventType = requiredText(root, "event_type");
        OffsetDateTime occurredAt = requiredDateTime(root, "occurred_at");
        requiredText(root, "source");
        UUID correlationId = requiredUuid(root, "correlation_id");
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("Falta el campo obligatorio data");
        }
        UUID driverId;
        if (ProcessDriverEvent.DOCUMENT_EXPIRED_EVENT.equals(eventType)) {
            driverId = documentOwnerDriverId(data, true);
        } else if (ProcessDriverEvent.DOCUMENT_RENEWED_EVENT.equals(eventType)) {
            driverId = documentOwnerDriverId(data, false);
        } else {
            driverId = requiredUuid(data, "driver_id");
        }
        return driverId == null ? null : new Command(eventId, eventType, driverId, occurredAt, correlationId);
    }

    /**
     * Validates the data of a Documentación event (HU-04 c2, HU-05 c2) and returns the driver it belongs to,
     * or null when owner_type is not DRIVER. The document fields are required whatever the owner; document_type
     * is not filtered (c3). Only document_expired_event requires expiration_date.
     *
     * @param data               data object of the event
     * @param requiresExpiration true for document_expired_event, false for document_renewed_event
     */
    private static UUID documentOwnerDriverId(JsonNode data, boolean requiresExpiration) {
        requiredText(data, "document_id");
        requiredText(data, "document_type");
        String ownerType = requiredText(data, "owner_type");
        requiredText(data, "owner_id");
        if (requiresExpiration) {
            requiredText(data, "expiration_date");
        }
        return DRIVER_OWNER.equals(ownerType) ? requiredUuid(data, "owner_id") : null;
    }

    private JsonNode parseJson(String payload) {
        try {
            return jsonMapper.readTree(payload);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Mensaje con formato JSON inválido", ex);
        }
    }

    private JsonNode unwrapSnsNotification(JsonNode root) {
        if ("Notification".equals(textOrUnknown(root, "Type")) && root.has("Message")) {
            return parseJson(root.get("Message").asString());
        }
        return root;
    }

    private static String textOrUnknown(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString() || value.asString().isBlank()) {
            return UNKNOWN;
        }
        return value.asString().trim();
    }

    private static String requiredText(JsonNode node, String field) {
        String text = textOrUnknown(node, field);
        if (UNKNOWN.equals(text)) {
            throw new IllegalArgumentException("Falta el campo obligatorio " + field);
        }
        return text;
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        String text = requiredText(node, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("El campo " + field + " no es un UUID válido: " + text, ex);
        }
    }

    private static OffsetDateTime requiredDateTime(JsonNode node, String field) {
        String text = requiredText(node, field);
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("El campo " + field + " no es una fecha ISO 8601 con offset: "
                    + text, ex);
        }
    }

    private static boolean isLastAttempt(String receiveCount) {
        if (receiveCount == null) {
            return false;
        }
        try {
            return Integer.parseInt(receiveCount.trim()) >= MAX_RECEIVES;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
