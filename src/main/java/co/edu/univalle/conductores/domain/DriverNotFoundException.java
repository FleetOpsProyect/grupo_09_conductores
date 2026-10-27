package co.edu.univalle.conductores.domain;

import java.util.UUID;

/**
 * Se lanza cuando no existe un conductor con el id indicado (HTTP 404).
 */
public class DriverNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción indicando el id buscado.
     *
     * @param id identificador que no existe
     */
    public DriverNotFoundException(UUID id) {
        super("No existe un conductor con id " + id);
    }
}
