package co.edu.univalle.conductores.application.port.in;

import co.edu.univalle.conductores.domain.Driver;
import java.util.UUID;

/**
 * Puerto de entrada: actualizar los datos editables de un conductor.
 */
public interface UpdateDriver {

    /**
     * Actualiza un conductor.
     *
     * @param id identificador del conductor
     * @param command datos nuevos
     * @return el conductor actualizado
     */
    Driver update(UUID id, Command command);

    /**
     * Datos editables del conductor.
     *
     * @param fullName nombre completo
     * @param email correo electrónico
     * @param phone teléfono opcional
     */
    record Command(String fullName, String email, String phone) {
    }
}
