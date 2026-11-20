package co.edu.univalle.conductores.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body to update a driver (HU-08). The document number is not editable.
 *
 * <p>It is a class with setters, not a record, because phone has three cases that a record cannot tell
 * apart: Jackson calls {@link #setPhone(String)} when the key arrives, even with {@code null}, and does
 * not call it when the key is missing.
 */
public class UpdateDriverRequest {

    @NotBlank
    @Size(min = 3, max = 100)
    @Schema(description = "Nombre completo, de 3 a 100 caracteres sin contar los espacios al inicio y al final.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    @Size(max = 100)
    @Schema(description = "Correo con formato usuario@dominio.ext, máximo 100 caracteres.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Pattern(regexp = "^3[0-9]{9}$")
    @Schema(description = """
            Opcional: 10 dígitos que empiezan por 3. Si la clave no llega, se mantiene el teléfono \
            guardado; si llega null, se borra; si llega un valor, se reemplaza.""",
            nullable = true)
    private String phone;

    @JsonIgnore
    private boolean phonePresent;

    /**
     * Returns the full name, already trimmed.
     *
     * @return full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the full name, trimmed so its length rule is checked without leading and trailing spaces.
     *
     * @param fullName full name as received
     */
    public void setFullName(String fullName) {
        this.fullName = fullName == null ? null : fullName.trim();
    }

    /**
     * Returns the email.
     *
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email.
     *
     * @param email email as received
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the phone; null when it was sent as null or not sent at all (see {@link #isPhonePresent()}).
     *
     * @return phone
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone and records that the key arrived in the body, even with a null value.
     *
     * @param phone phone as received, may be null
     */
    public void setPhone(String phone) {
        this.phone = phone;
        this.phonePresent = true;
    }

    /**
     * Tells whether the body had the phone key.
     *
     * @return true when phone arrived, with a value or null
     */
    @JsonIgnore
    public boolean isPhonePresent() {
        return phonePresent;
    }
}
