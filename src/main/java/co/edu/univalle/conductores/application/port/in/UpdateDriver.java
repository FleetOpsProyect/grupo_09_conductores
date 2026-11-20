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
     * Datos editables del conductor (HU-08, criterio 1).
     *
     * @param fullName nombre completo, obligatorio
     * @param email correo electrónico, obligatorio
     * @param phonePresent true si la petición trae phone; false para mantener el teléfono guardado
     * @param phone teléfono nuevo, o null para borrarlo; se ignora si phonePresent es false
     */
    record Command(String fullName, String email, boolean phonePresent, String phone) {

        /**
         * Reemplaza el teléfono por el valor dado; null lo borra.
         *
         * @param fullName nombre completo
         * @param email correo electrónico
         * @param phone teléfono nuevo, o null para borrarlo
         */
        public Command(String fullName, String email, String phone) {
            this(fullName, email, true, phone);
        }

        /**
         * Cambia nombre y correo y mantiene el teléfono guardado.
         *
         * @param fullName nombre completo
         * @param email correo electrónico
         * @return comando que no toca el teléfono
         */
        public static Command keepingPhone(String fullName, String email) {
            return new Command(fullName, email, false, null);
        }
    }
}
