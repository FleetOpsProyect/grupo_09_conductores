package co.edu.univalle.conductores.application.port.in;

import co.edu.univalle.conductores.domain.Driver;
import java.util.UUID;

/**
 * Puerto de entrada: consultar un conductor por id.
 */
public interface GetDriver {

    /**
     * Obtiene un conductor.
     *
     * @param id identificador del conductor
     * @return el conductor encontrado
     */
    Driver get(UUID id);
}
