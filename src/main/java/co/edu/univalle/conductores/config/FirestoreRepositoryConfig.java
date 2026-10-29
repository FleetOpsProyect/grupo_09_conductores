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
     * Adaptador de persistencia de conductores.
     *
     * @param firestore cliente de Firestore
     * @return implementación de DriverRepository
     */
    @Bean
    public DriverRepository driverRepository(Firestore firestore) {
        return new FirestoreDriverAdapter(firestore);
    }
}
