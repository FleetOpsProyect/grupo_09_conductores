package co.edu.univalle.conductores.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del cliente de Firestore.
 */
@Configuration
public class FirestoreConfig {

    /**
     * Crea el cliente de Firestore apuntando al emulador si hay emulator-host configurado.
     *
     * @param projectId id del proyecto de Firebase
     * @param emulatorHost host:puerto del emulador, vacío en producción
     * @return cliente de Firestore
     */
    @Bean
    public Firestore firestore(@Value("${app.firebase.project-id}") String projectId,
            @Value("${app.firebase.emulator-host:}") String emulatorHost) {
        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder().setProjectId(projectId);
        if (!emulatorHost.isBlank()) {
            builder.setEmulatorHost(emulatorHost);
        }
        return builder.build().getService();
    }
}
