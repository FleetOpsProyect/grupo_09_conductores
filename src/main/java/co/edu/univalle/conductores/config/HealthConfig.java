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
     * Indicador de salud de Firestore.
     *
     * @param firestore cliente de Firestore
     * @return indicador de salud
     */
    @Bean
    public HealthIndicator firestoreHealthIndicator(Firestore firestore) {
        return new FirestoreHealthIndicator(firestore);
    }
}
