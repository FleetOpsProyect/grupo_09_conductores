package co.edu.univalle.conductores.application.event;

import java.util.UUID;

/**
 * Datos publicados cuando un conductor queda activo.
 *
 * @param driverId identificador del conductor
 * @param fullName nombre completo del conductor
 */
public record DriverActivatedPayload(UUID driverId, String fullName) {
}