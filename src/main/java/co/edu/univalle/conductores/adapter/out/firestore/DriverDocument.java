package co.edu.univalle.conductores.adapter.out.firestore;

import co.edu.univalle.conductores.domain.Driver;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo del documento de la colección drivers en Firestore.
 */
final class DriverDocument {

    static final String COLLECTION = "drivers";

    private DriverDocument() {
    }

    /**
     * Convierte un conductor del dominio a un mapa para Firestore.
     *
     * @param driver conductor del dominio
     * @return campos del documento
     */
    static Map<String, Object> fromDomain(Driver driver) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", driver.getId().toString());
        data.put("full_name", driver.getFullName());
        data.put("document_number", driver.getDocumentNumber());
        data.put("email", driver.getEmail());
        data.put("phone", driver.getPhone());
        data.put("status", driver.getStatus().name());
        data.put("document_block", driver.isDocumentBlock());
        data.put("medical_block", driver.isMedicalBlock());
        data.put("created_at", driver.getCreatedAt().toString());
        data.put("updated_at", driver.getUpdatedAt().toString());
        return data;
    }

    /**
     * Campos que cambia la modificación de un conductor (HU-08): los datos editables y updated_at. No
     * incluye status ni los bloqueos, para no pisar los que haya aplicado el consumidor.
     *
     * @param driver conductor del dominio con los datos nuevos
     * @return campos a actualizar en Firestore
     */
    static Map<String, Object> editableFields(Driver driver) {
        Map<String, Object> data = new HashMap<>();
        data.put("full_name", driver.getFullName());
        data.put("email", driver.getEmail());
        data.put("phone", driver.getPhone());
        data.put("updated_at", driver.getUpdatedAt().toString());
        return data;
    }

    /**
     * Convierte un conductor del dominio incluyendo notas y fecha de alta médica.
     *
     * @param driver conductor del dominio
     * @param notes observaciones del alta médica (puede ser null)
     * @param releasedAt momento del alta médica
     * @return campos del documento para Firestore
     */
    static Map<String, Object> fromDomainWithMedicalRelease(
            Driver driver, String notes, OffsetDateTime releasedAt) {
        Map<String, Object> data = fromDomain(driver);
        data.put("medical_release_notes", notes);
        data.put("medical_released_at", releasedAt != null ? releasedAt.toString() : null);
        return data;
    }

    /**
     * Reconstruye el conductor del dominio. El campo status se ignora: se recalcula desde los bloqueos.
     *
     * @param data campos del documento
     * @return conductor del dominio
     */
    static Driver toDomain(Map<String, Object> data) {
        return Driver.restore(
                UUID.fromString((String) data.get("id")),
                (String) data.get("document_number"),
                (String) data.get("full_name"),
                (String) data.get("email"),
                (String) data.get("phone"),
                Boolean.TRUE.equals(data.get("document_block")),
                Boolean.TRUE.equals(data.get("medical_block")),
                OffsetDateTime.parse((String) data.get("created_at")),
                OffsetDateTime.parse((String) data.get("updated_at")));
    }
}
