package co.edu.univalle.conductores.domain;

/**
 * Se lanza cuando una petición intenta cambiar un campo que no es editable, como el número de documento
 * en la modificación de un conductor (HU-08, criterio 2).
 */
public class FieldNotEditableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    /**
     * Crea la excepción para un campo.
     *
     * @param field nombre del campo tal como llega en el JSON, por ejemplo document_number
     */
    public FieldNotEditableException(String field) {
        super("El campo " + field + " no es editable");
        this.field = field;
    }

    /**
     * Devuelve el nombre del campo no editable.
     *
     * @return nombre del campo en el JSON
     */
    public String getField() {
        return field;
    }
}
