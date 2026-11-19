package co.edu.univalle.conductores.domain;

/**
 * Thrown when the persistence store does not answer in time or is temporarily unreachable (HTTP 503).
 *
 * <p>It never means "not found": a caller that gets this exception does not know whether the data exists.
 */
public class PersistenceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message technical description, for logs only
     * @param cause original failure
     */
    public PersistenceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
