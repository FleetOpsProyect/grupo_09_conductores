package co.edu.univalle.conductores.config;

import co.edu.univalle.conductores.adapter.out.firestore.FirestoreHealthIndicator;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de los indicadores de salud.
 */
@Configuration
public class HealthConfig {

    /**
     * Firestore health indicator, bounded by the configured Firestore timeout.
     *
     * @param firestore Firestore client
     * @param properties Firestore settings
     * @return health indicator
     */
    @Bean
    public HealthIndicator firestoreHealthIndicator(Firestore firestore, FirestoreProperties properties) {
        return new FirestoreHealthIndicator(firestore, properties.timeout());
    }
}
