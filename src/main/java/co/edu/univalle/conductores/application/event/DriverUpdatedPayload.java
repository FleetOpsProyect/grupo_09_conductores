package co.edu.univalle.conductores.application.event;

import java.util.UUID;

/**
 * Datos publicados cuando cambian los datos editables de un conductor (HU-08).
 *
 * @param driverId identificador del conductor
 * @param fullName nombre completo del conductor, ya con el valor nuevo
 */
public record DriverUpdatedPayload(UUID driverId, String fullName) {
}
