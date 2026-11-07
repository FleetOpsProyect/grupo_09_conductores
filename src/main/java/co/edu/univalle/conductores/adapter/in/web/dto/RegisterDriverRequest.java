package co.edu.univalle.conductores.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body to register a driver. The status is not accepted: it is ignored if sent.
 *
 * @param fullName full name, 3 to 100 characters not counting leading and trailing spaces
 * @param documentNumber identity document number, digits only, 6 to 10
 * @param email email in the form user@domain.ext, up to 100 characters
 * @param phone optional phone number, 10 digits starting with 3
 */
public record RegisterDriverRequest(
        @NotBlank @Size(min = 3, max = 100) String fullName,
        @NotBlank @Pattern(regexp = "^[0-9]{6,10}$") String documentNumber,
        @NotBlank @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") @Size(max = 100) String email,
        @Pattern(regexp = "^3[0-9]{9}$") String phone) {

    /**
     * Trims full_name so its length rule is checked without leading and trailing spaces.
     */
    public RegisterDriverRequest {
        fullName = fullName == null ? null : fullName.trim();
    }
}
