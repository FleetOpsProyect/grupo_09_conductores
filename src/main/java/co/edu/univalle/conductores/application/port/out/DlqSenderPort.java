package co.edu.univalle.conductores.application.port.out;

/**
 * Puerto para enviar mensajes a la cola de mensajes muertos (DLQ).
 */
public interface DlqSenderPort {

    /**
     * Envía un mensaje inválido o irrecuperable a la DLQ.
     *
     * @param rawPayload cuerpo crudo del mensaje
     * @param eventType tipo de evento, si pudo ser identificado
     * @param reason causa del desvío a la DLQ
     */
    void sendToDlq(String rawPayload, String eventType, String reason);
}

