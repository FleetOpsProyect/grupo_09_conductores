package co.edu.univalle.conductores.domain;

/**
 * Se lanza cuando los datos de un conductor no cumplen las reglas del dominio.
 */
public class InvalidDriverException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con la descripción del dato inválido.
     *
     * @param message descripción del dato inválido
     */
    public InvalidDriverException(String message) {
        super(message);
    }
}
