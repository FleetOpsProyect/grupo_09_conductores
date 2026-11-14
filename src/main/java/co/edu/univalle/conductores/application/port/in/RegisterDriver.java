package co.edu.univalle.conductores.application.port.in;

import co.edu.univalle.conductores.domain.Driver;
import java.util.UUID;

/**
 * Puerto de entrada: registrar un conductor nuevo.
 */
public interface RegisterDriver {

    /**
     * Registra un conductor.
     *
     * @param command datos del conductor
     * @return el conductor creado
     */
    Driver register(Command command);

    /**
     * Datos necesarios para registrar un conductor.
     *
     * @param fullName nombre completo
     * @param documentNumber número de documento
     * @param email correo electrónico
     * @param phone teléfono opcional
     * @param correlationId identificador de correlación de la operación HTTP
     */
    record Command(
            String fullName,
            String documentNumber,
            String email,
            String phone,
            UUID correlationId) {
    }
}
