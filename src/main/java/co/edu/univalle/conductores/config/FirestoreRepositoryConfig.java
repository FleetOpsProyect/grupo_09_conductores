package co.edu.univalle.conductores.config;

import co.edu.univalle.conductores.adapter.out.firestore.FirestoreDriverAdapter;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el adaptador de Firestore como implementación de DriverRepository.
 */
@Configuration
public class FirestoreRepositoryConfig {

    /**
     * Driver persistence adapter, bounded by the configured Firestore timeout.
     *
     * @param firestore Firestore client
     * @param properties Firestore settings
     * @return DriverRepository implementation
     */
    @Bean
    public DriverRepository driverRepository(Firestore firestore, FirestoreProperties properties) {
        return new FirestoreDriverAdapter(firestore, properties.timeout());
    }
}
