package co.edu.univalle.conductores.application.port.out;

import co.edu.univalle.conductores.application.event.OutboxEvent;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PageResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

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
     * Actualiza los datos editables de un conductor y registra el evento del outbox en una sola
     * transacción. Solo escribe full_name, email, phone y updated_at: el status, los bloqueos y las notas
     * del alta médica guardados se conservan, aunque otro proceso los haya cambiado después de leer.
     *
     * @param driver conductor con los datos nuevos
     * @param outboxEvent evento driver_updated_event a guardar en outbox_events
     */
    void updateWithOutbox(Driver driver, OutboxEvent outboxEvent);

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
     * Lista los conductores candidatos para validar vigencia de licencia (excluyendo
     * {@link DriverStatus#MEDICAL_HOLD_INCIDENT}), ordenados de forma ascendente por {@code created_at}.
     *
     * @param status estado a filtrar, o {@code null} para incluir todos los estados elegibles
     * @return lista de conductores candidatos ordenados por {@code created_at}
     */
    List<Driver> findCandidates(DriverStatus status);

    /**
     * Registra el alta médica en una sola transacción: lee el conductor actual, le aplica {@code release},
     * que quita el bloqueo médico y decide el evento, y guarda el conductor, las notas, la fecha del alta y
     * el evento. Leer dentro de la transacción evita pisar un bloqueo que el consumidor haya aplicado entre
     * tanto: si el documento cambia antes de confirmar, Firestore repite la transacción y con ella
     * {@code release}.
     *
     * @param id identificador del conductor
     * @param notes observaciones del alta médica, o null
     * @param releasedAt momento en que se registró el alta
     * @param release cambio sobre el conductor leído; devuelve el evento del outbox, o null si no aplica
     * @return el conductor tal como quedó guardado
     * @throws co.edu.univalle.conductores.domain.DriverNotFoundException si el conductor no existe
     */
    Driver releaseMedical(UUID id, String notes, OffsetDateTime releasedAt, Function<Driver, OutboxEvent> release);
}
