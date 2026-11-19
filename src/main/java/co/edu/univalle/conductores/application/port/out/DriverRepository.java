package co.edu.univalle.conductores.application.port.out;

import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PageResult;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: lo que el núcleo necesita de la persistencia de conductores.
 */
public interface DriverRepository {

    /**
     * Guarda el conductor, reserva su número de documento y registra el evento
     * del outbox en una sola transacción.
     *
     * @param driver conductor a crear
     * @param outboxEvent evento que se publicará después del registro
     */
    void createWithUniquenessAndOutbox(Driver driver, OutboxEvent outboxEvent);

    /**
     * Actualiza un conductor existente.
     *
     * @param driver conductor con los datos nuevos
     */
    void save(Driver driver);

    /**
     * Busca un conductor por su id.
     *
     * @param id identificador del conductor
     * @return el conductor, si existe
     */
    Optional<Driver> findById(UUID id);

    /**
     * Lista conductores con filtro opcional por estado y paginación.
     *
     * @param status estado a filtrar, o null para no filtrar
     * @param page número de página desde 0
     * @param size tamaño de página
     * @return una página de conductores
     */
    PageResult<Driver> findAll(DriverStatus status, int page, int size);

    /**
     * Guarda el alta médica de un conductor y registra opcionalmente el evento en el outbox
     * en una sola transacción.
     *
     * @param driver conductor con el bloqueo médico removido
     * @param notes observaciones o notas del alta médica (opcional, puede ser null)
     * @param releasedAt momento en que se registró el alta
     * @param outboxEvent evento a guardar en outbox_events, o null si no aplica
     */
    void saveMedicalRelease(Driver driver, String notes, OffsetDateTime releasedAt, OutboxEvent outboxEvent);
}
