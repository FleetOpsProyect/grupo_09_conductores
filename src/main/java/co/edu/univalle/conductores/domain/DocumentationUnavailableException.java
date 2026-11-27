package co.edu.univalle.conductores.domain;

/**
 * Se lanza cuando el servicio de Documentación no responde a tiempo, devuelve 5xx
 * o el circuito está abierto (HTTP 503 DEPENDENCY_UNAVAILABLE).
 */
public class DocumentationUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con un mensaje técnico para los logs.
     *
     * @param message descripción técnica del fallo
     */
    public DocumentationUnavailableException(String message) {
        super(message);
    }

    /**
     * Crea la excepción con un mensaje técnico y la causa original.
     *
     * @param message descripción técnica del fallo
     * @param cause   fallo original
     */
    public DocumentationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
