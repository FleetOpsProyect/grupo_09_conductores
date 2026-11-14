package co.edu.univalle.conductores.config;

import co.edu.univalle.conductores.adapter.out.firestore.FirestoreDriverAdapter;
import com.google.cloud.firestore.Firestore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

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
    * @param jsonMapper mapper configured by Spring
     * @return DriverRepository implementation
     */
    @Bean
    public FirestoreDriverAdapter driverRepository(
            Firestore firestore,
            FirestoreProperties properties,
            JsonMapper jsonMapper) {
        return new FirestoreDriverAdapter(firestore, properties.timeout(), jsonMapper);
    }
}
