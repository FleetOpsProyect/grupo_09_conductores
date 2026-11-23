package co.edu.univalle.conductores.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request body for registering a medical release of a driver.
 *
 * @param notes optional medical release notes or observations, up to 500
 *              characters
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedicalReleaseRequest(
        @Schema(
                description = "Notas médicas u observaciones del alta",
                maxLength = 500,
                example = "Alta médica completa sin secuelas")
        @Size(max = 500)
        String notes) {
}
