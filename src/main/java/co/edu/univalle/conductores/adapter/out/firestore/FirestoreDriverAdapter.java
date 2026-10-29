package co.edu.univalle.conductores.adapter.out.firestore;

import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.PageResult;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Adaptador de salida: implementa DriverRepository con Firestore.
 */
public class FirestoreDriverAdapter implements DriverRepository {

    private static final String DOCUMENT_NUMBERS = "document_numbers";

    private final Firestore firestore;

    /**
     * Crea el adaptador con el cliente de Firestore.
     *
     * @param firestore cliente de Firestore
     */
    public FirestoreDriverAdapter(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void createWithUniqueness(Driver driver) {
        String documentNumber = driver.getDocumentNumber();
        await(firestore.runTransaction(transaction -> {
            DocumentReference numberRef = firestore.collection(DOCUMENT_NUMBERS).document(documentNumber);
            if (transaction.get(numberRef).get().exists()) {
                throw new DuplicateDocumentException(documentNumber);
            }
            transaction.set(numberRef, Map.of("driver_id", driver.getId().toString()));
            transaction.set(drivers().document(driver.getId().toString()), DriverDocument.fromDomain(driver));
            return null;
        }));
    }

    @Override
    public void save(Driver driver) {
        await(drivers().document(driver.getId().toString()).set(DriverDocument.fromDomain(driver)));
    }

    @Override
    public Optional<Driver> findById(UUID id) {
        DocumentSnapshot snapshot = await(drivers().document(id.toString()).get());
        return snapshot.exists()
                ? Optional.of(DriverDocument.toDomain(snapshot.getData()))
                : Optional.empty();
    }

    @Override
    public PageResult<Driver> findAll(DriverStatus status, int page, int size) {
        Query query = drivers().orderBy("created_at");
        if (status != null) {
            query = query.whereEqualTo("status", status.name());
        }
        AggregateQuerySnapshot count = await(query.count().get());
        QuerySnapshot snapshot = await(query.offset(page * size).limit(size).get());
        List<Driver> content = snapshot.getDocuments().stream()
                .map(document -> DriverDocument.toDomain(document.getData()))
                .toList();
        return new PageResult<>(content, page, size, count.getCount());
    }

    private CollectionReference drivers() {
        return firestore.collection(DriverDocument.COLLECTION);
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operación de Firestore interrumpida", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Error al acceder a Firestore", e.getCause());
        }
    }
}
