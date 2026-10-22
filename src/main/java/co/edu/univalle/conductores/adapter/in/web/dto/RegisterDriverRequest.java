package co.edu.univalle.conductores.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición para registrar un conductor. El estado no se acepta: se ignora si llega.
 *
 * @param fullName nombre completo, de 3 a 100 caracteres
 * @param documentNumber número de documento, solo dígitos, de 6 a 10
 * @param email correo electrónico, máximo 100 caracteres
 * @param phone teléfono opcional de 10 dígitos que empieza por 3
 */
public record RegisterDriverRequest(
        @NotBlank @Size(min = 3, max = 100) String fullName,
        @NotBlank @Pattern(regexp = "^[0-9]{6,10}$") String documentNumber,
        @NotBlank @Email @Size(max = 100) String email,
        @Pattern(regexp = "^3[0-9]{9}$") String phone) {
}
