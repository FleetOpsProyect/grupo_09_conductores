package co.edu.univalle.conductores.domain;

/**
 * Se lanza cuando la operación no está permitida en el estado actual del conductor.
 */
public class StatusNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con la descripción del motivo por el cual el estado no permite la operación.
     *
     * @param message descripción del error
     */
    public StatusNotAllowedException(String message) {
        super(message);
    }
}
