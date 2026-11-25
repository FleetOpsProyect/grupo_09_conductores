package co.edu.univalle.conductores.application.event;

import java.util.UUID;

/**
 * Datos publicados cuando un conductor deja de estar activo por un bloqueo.
 *
 * @param driverId identificador del conductor
 * @param fullName nombre completo del conductor
 */
public record DriverBlockedPayload(UUID driverId, String fullName) {
}
