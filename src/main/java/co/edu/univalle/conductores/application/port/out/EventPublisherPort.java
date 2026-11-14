package co.edu.univalle.conductores.application.port.out;

import co.edu.univalle.conductores.application.event.EventEnvelope;

/**
 * Puerto para publicar eventos en el broker.
 */
public interface EventPublisherPort {

    /**
     * Publica el envelope del evento.
     *
     * @param event evento que se publicará
     */
    void publish(EventEnvelope<?> event);
}
