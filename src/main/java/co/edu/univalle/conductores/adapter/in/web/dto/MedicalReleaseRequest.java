package co.edu.univalle.conductores.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for registering a medical release of a driver.
 *
 * @param notes optional medical release notes or observations
 */
public record MedicalReleaseRequest(
        @Size(max = 500) String notes) {
}
