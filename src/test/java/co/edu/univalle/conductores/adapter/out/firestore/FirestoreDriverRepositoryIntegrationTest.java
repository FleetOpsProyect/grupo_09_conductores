package co.edu.univalle.conductores.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.conductores.BaseIntegrationTest;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import co.edu.univalle.conductores.domain.PageResult;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FirestoreDriverRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T16:00:00-05:00");

    @Autowired
    private DriverRepository repository;

    @Test
    void createsAndFindsDriver() {
        Driver driver = newDriver(uniqueDocument(), false);

        repository.createWithUniqueness(driver);

        Driver found = repository.findById(driver.getId()).orElseThrow();
        assertThat(found.getDocumentNumber()).isEqualTo(driver.getDocumentNumber());
        assertThat(found.getStatus()).isEqualTo(DriverStatus.ACTIVE);
    }

    @Test
    void rejectsDuplicateDocumentAndStoresNothingForSecondDriver() {
        String documentNumber = uniqueDocument();
        repository.createWithUniqueness(newDriver(documentNumber, false));
        Driver second = newDriver(documentNumber, false);

        assertThatThrownBy(() -> repository.createWithUniqueness(second))
                .isInstanceOf(DuplicateDocumentException.class);
        assertThat(repository.findById(second.getId())).isEmpty();
    }

    @Test
    void saveUpdatesStoredDriver() {
        Driver driver = newDriver(uniqueDocument(), false);
        repository.createWithUniqueness(driver);

        driver.update("Nombre Actualizado", "nuevo@correo.com", null, NOW.plusHours(1));
        repository.save(driver);

        assertThat(repository.findById(driver.getId()).orElseThrow().getFullName())
                .isEqualTo("Nombre Actualizado");
    }

    @Test
    void findAllFiltersByStatus() {
        Driver active = newDriver(uniqueDocument(), false);
        Driver blocked = newDriver(uniqueDocument(), true);
        repository.createWithUniqueness(active);
        repository.createWithUniqueness(blocked);

        PageResult<Driver> page = repository.findAll(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS, 0, 100);

        assertThat(page.content()).extracting(Driver::getId)
                .contains(blocked.getId())
                .doesNotContain(active.getId());
    }

    private static Driver newDriver(String documentNumber, boolean documentBlock) {
        return Driver.restore(UUID.randomUUID(), documentNumber, "Carlos Pérez", "carlos@correo.com", null,
                documentBlock, false, NOW, NOW);
    }

    private static String uniqueDocument() {
        return String.format("%010d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }
}
