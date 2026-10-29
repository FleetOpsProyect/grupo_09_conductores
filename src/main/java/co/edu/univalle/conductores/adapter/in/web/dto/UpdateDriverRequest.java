package co.edu.univalle.conductores.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición para actualizar un conductor. El número de documento no es editable.
 *
 * @param fullName nombre completo, de 3 a 100 caracteres
 * @param email correo electrónico, máximo 100 caracteres
 * @param phone teléfono opcional de 10 dígitos que empieza por 3
 */
public record UpdateDriverRequest(
        @NotBlank @Size(min = 3, max = 100) String fullName,
        @NotBlank @Email @Size(max = 100) String email,
        @Pattern(regexp = "^3[0-9]{9}$") String phone) {
}
