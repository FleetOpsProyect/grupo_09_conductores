package co.edu.univalle.conductores.application.event;

import java.util.Objects;

/**
 * Evento pendiente de publicación o ya publicado.
 *
 * @param envelope contenido del evento
 * @param status estado actual en el outbox
 * @param attempts cantidad de intentos de publicación
 */
public record OutboxEvent(EventEnvelope<?> envelope, Status status, int attempts) {

    /**
     * Validates the event and its publication state.
     */
    public OutboxEvent {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(status, "status");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts no puede ser negativo");
        }
    }

    /**
     * Crea el registro inicial del outbox.
     *
     * @param envelope evento que se publicará
     * @return evento pendiente, con cero intentos
     */
    public static OutboxEvent pending(EventEnvelope<?> envelope) {
        return new OutboxEvent(envelope, Status.PENDING, 0);
    }

    /**
     * Estado de publicación del evento.
     */
    public enum Status {
        PENDING,
        SENT
    }
}