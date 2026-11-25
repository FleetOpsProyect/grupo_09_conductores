package co.edu.univalle.conductores.application.port.out;

import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.domain.Driver;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * Puerto de salida para aplicar un evento consumido a un conductor exactamente una vez.
 */
public interface ProcessedEventRepositoryPort {

    /**
     * En una sola transacción: si el evento no está en processed_events, carga el conductor, le aplica
     * {@code change}, lo guarda, registra el evento en processed_events y, si {@code change} devuelve un
     * evento, lo guarda en outbox_events. Si el evento ya estaba registrado, no modifica nada. Si el
     * documento cambia antes de confirmar, Firestore repite la transacción y con ella {@code change}.
     *
     * @param eventId identificador único del evento (id del documento en processed_events)
     * @param eventType tipo de evento
     * @param driverId identificador del conductor afectado
     * @param processedAt instante de procesamiento; la retención se cuenta desde aquí
     * @param change cambio de dominio sobre el conductor cargado; devuelve el evento del outbox, o null si
     *        no corresponde publicar ninguno
     * @return {@code true} si se aplicó el evento, {@code false} si ya había sido procesado
     * @throws co.edu.univalle.conductores.domain.DriverNotFoundException si el conductor no existe
     */
    boolean applyOnce(UUID eventId, String eventType, UUID driverId, Instant processedAt,
            Function<Driver, OutboxEvent> change);
}
