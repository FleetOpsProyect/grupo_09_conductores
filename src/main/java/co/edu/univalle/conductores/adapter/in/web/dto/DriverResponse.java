package co.edu.univalle.conductores.adapter.in.web.dto;

import co.edu.univalle.conductores.domain.Driver;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representación de un conductor en la API.
 *
 * @param id identificador UUID
 * @param fullName nombre completo
 * @param documentNumber número de documento
 * @param email correo electrónico
 * @param phone teléfono
 * @param status estado calculado
 * @param createdAt fecha de creación en GMT-5
 * @param updatedAt fecha de última modificación en GMT-5
 */
public record DriverResponse(UUID id, String fullName, String documentNumber, String email, String phone,
        String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    /**
     * Convierte el conductor del dominio a su respuesta.
     *
     * @param driver conductor del dominio
     * @return respuesta de la API
     */
    public static DriverResponse from(Driver driver) {
        return new DriverResponse(driver.getId(), driver.getFullName(), driver.getDocumentNumber(),
                driver.getEmail(), driver.getPhone(), driver.getStatus().name(),
                driver.getCreatedAt(), driver.getUpdatedAt());
    }
}
