package co.edu.univalle.conductores.domain;

import java.util.UUID;

/**
 * Thrown when no driver exists with the given id (HTTP 404).
 */
public class DriverNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for a well-formed id that does not exist.
     *
     * @param id id that does not exist
     */
    public DriverNotFoundException(UUID id) {
        this(String.valueOf(id));
    }

    /**
     * Creates the exception for the id exactly as the client sent it, even when it is not a valid UUID.
     *
     * @param id id as received
     */
    public DriverNotFoundException(String id) {
        super("No existe un conductor con id '" + id + "'");
    }
}
