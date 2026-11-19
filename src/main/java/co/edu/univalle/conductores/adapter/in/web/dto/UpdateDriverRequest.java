package co.edu.univalle.conductores.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body to update a driver. The document number is not editable.
 *
 * @param fullName full name, 3 to 100 characters not counting leading and trailing spaces
 * @param email email in the form user@domain.ext, up to 100 characters
 * @param phone optional phone number, 10 digits starting with 3
 */
public record UpdateDriverRequest(
        @NotBlank @Size(min = 3, max = 100) String fullName,
        @NotBlank @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") @Size(max = 100) String email,
        @Pattern(regexp = "^3[0-9]{9}$") String phone) {

    /**
     * Trims full_name so its length rule is checked without leading and trailing spaces.
     */
    public UpdateDriverRequest {
        fullName = fullName == null ? null : fullName.trim();
    }
}
