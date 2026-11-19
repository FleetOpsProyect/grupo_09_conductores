package co.edu.univalle.conductores.application.port.out;

import co.edu.univalle.conductores.domain.Driver;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Puerto de salida para aplicar un evento consumido a un conductor exactamente una vez.
 */
public interface ProcessedEventRepositoryPort {

    /**
     * En una sola transacción: si el evento no está en processed_events, carga el conductor, le aplica
     * {@code change}, lo guarda y registra el evento en processed_events. Si el evento ya estaba
     * registrado, no modifica nada.
     *
     * @param eventId identificador único del evento (id del documento en processed_events)
     * @param eventType tipo de evento
     * @param driverId identificador del conductor afectado
     * @param processedAt instante de procesamiento; la retención se cuenta desde aquí
     * @param change cambio de dominio a aplicar sobre el conductor cargado
     * @return {@code true} si se aplicó el evento, {@code false} si ya había sido procesado
     * @throws co.edu.univalle.conductores.domain.DriverNotFoundException si el conductor no existe
     */
    boolean applyOnce(UUID eventId, String eventType, UUID driverId, Instant processedAt, Consumer<Driver> change);
}
