package co.edu.univalle.conductores.adapter.out.firestore;

import com.google.cloud.firestore.Firestore;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Indicador de salud que comprueba la conectividad con Firestore.
 */
public class FirestoreHealthIndicator implements HealthIndicator {

    private static final long TIMEOUT_SECONDS = 2;

    private final Firestore firestore;

    /**
     * Crea el indicador con el cliente de Firestore.
     *
     * @param firestore cliente de Firestore
     */
    public FirestoreHealthIndicator(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Comprueba Firestore con una lectura acotada y sin escrituras.
     *
     * @return estado UP si Firestore responde, DOWN en caso contrario
     */
    @Override
    public Health health() {
        try {
            firestore.collection(DriverDocument.COLLECTION).limit(1).get().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Health.up().build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.down(ex).build();
        } catch (ExecutionException | TimeoutException ex) {
            return Health.down(ex).build();
        }
    }
}
