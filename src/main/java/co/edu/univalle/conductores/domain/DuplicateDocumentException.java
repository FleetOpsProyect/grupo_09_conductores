package co.edu.univalle.conductores.domain;

/**
 * Se lanza cuando ya existe un conductor con el mismo número de documento (HTTP 409).
 */
public class DuplicateDocumentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción indicando el documento repetido.
     *
     * @param documentNumber número de documento que ya existe
     */
    public DuplicateDocumentException(String documentNumber) {
        super("Ya existe un conductor con document_number " + documentNumber);
    }
}
