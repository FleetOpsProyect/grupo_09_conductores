package co.edu.univalle.conductores.application.port.in;

import co.edu.univalle.conductores.domain.Driver;
import java.util.UUID;

/**
 * Puerto de entrada: registrar alta médica de un conductor.
 */
public interface ReleaseMedicalBlock {

    /**
     * Registra el alta médica de un conductor para habilitar su estado operativo.
     *
     * @param id identificador del conductor
     * @param command datos del alta médica
     * @return el conductor actualizado
     */
    Driver release(UUID id, Command command);

    /**
     * Datos del alta médica.
     *
     * @param notes notas médicas u observaciones del alta (opcional)
     * @param correlationId identificador de correlación para trazabilidad
     */
    record Command(String notes, UUID correlationId) {

        /**
         * Constructor con correlationId aleatorio.
         *
         * @param notes notas médicas u observaciones
         */
        public Command(String notes) {
            this(notes, UUID.randomUUID());
        }
    }
}
