package co.edu.univalle.conductores.adapter.in.web;

import org.springframework.validation.BindingResult;

/**
 * Invalid request body detected by a controller that completes the validation errors itself, such as
 * PUT /drivers/{id}, which adds NOT_EDITABLE for document_number to the errors of the body (HU-08, c5).
 * {@link GlobalExceptionHandler} answers it like a failed {@code @Valid}: 400 INVALID_DATA with every field.
 */
class InvalidRequestBodyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient BindingResult bindingResult;

    /**
     * Creates the exception with the body errors.
     *
     * @param bindingResult validation result of the body, with every invalid field
     */
    InvalidRequestBodyException(BindingResult bindingResult) {
        super("Invalid request body");
        this.bindingResult = bindingResult;
    }

    /**
     * Returns the validation result of the body.
     *
     * @return binding result with every invalid field
     */
    BindingResult getBindingResult() {
        return bindingResult;
    }
}
