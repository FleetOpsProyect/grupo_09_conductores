package co.edu.univalle.conductores.adapter.out.documentacion;

import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import co.edu.univalle.conductores.domain.DocumentationUnavailableException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementación provisional de {@link DocumentValidityPort} para permitir el arranque de la
 * aplicación mientras se implementa el cliente HTTP real con Resilience4j en HU07-3.
 */
// PROVISIONAL: se reemplaza en HU07-3 (@Juan David López)
@Component
public class ProvisionalDocumentValidityAdapter implements DocumentValidityPort {

    @Override
    public Set<UUID> findDriversWithValidLicense(List<UUID> driverIds, LocalDate validUntil) {
        throw new DocumentationUnavailableException(
                "Adaptador provisional de Documentación activo hasta HU07-3");
    }
}
