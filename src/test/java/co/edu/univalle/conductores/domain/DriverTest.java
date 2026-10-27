package co.edu.univalle.conductores.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DriverTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T16:00:00-05:00");

    @Test
    void registerCreatesActiveDriverWithTrimmedFields() {
        Driver driver = Driver.register("  Carlos Pérez  ", "1144056789", " carlos@correo.com ", "3001234567", NOW);

        assertThat(driver.getId()).isNotNull();
        assertThat(driver.getFullName()).isEqualTo("Carlos Pérez");
        assertThat(driver.getEmail()).isEqualTo("carlos@correo.com");
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(driver.getCreatedAt()).isEqualTo(NOW);
        assertThat(driver.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void registerAcceptsMissingPhone() {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null, NOW);

        assertThat(driver.getPhone()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "12345678901", "12ab5678"})
    void registerRejectsInvalidDocumentNumber(String documentNumber) {
        assertThatThrownBy(() -> Driver.register("Carlos Pérez", documentNumber, "carlos@correo.com", null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("document_number");
    }

    @Test
    void registerRejectsNullDocumentNumber() {
        assertThatThrownBy(() -> Driver.register("Carlos Pérez", null, "carlos@correo.com", null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("document_number");
    }

    @Test
    void registerRejectsNullName() {
        assertThatThrownBy(() -> Driver.register(null, "1144056789", "carlos@correo.com", null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("full_name");
    }

    @Test
    void registerRejectsNameShorterThanThreeCharacters() {
        assertThatThrownBy(() -> Driver.register("Al", "1144056789", "carlos@correo.com", null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("full_name");
    }

    @Test
    void registerRejectsNameLongerThanOneHundredCharacters() {
        assertThatThrownBy(
                () -> Driver.register("A".repeat(101), "1144056789", "carlos@correo.com", null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("full_name");
    }

    @Test
    void registerRejectsNullEmail() {
        assertThatThrownBy(() -> Driver.register("Carlos Pérez", "1144056789", null, null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("email");
    }

    @Test
    void registerRejectsEmailLongerThanOneHundredCharacters() {
        assertThatThrownBy(() -> Driver.register("Carlos Pérez", "1144056789",
                "a".repeat(101) + "@correo.com", null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("email");
    }

    @Test
    void registerRejectsInvalidEmail() {
        assertThatThrownBy(() -> Driver.register("Carlos Pérez", "1144056789", "carlos.correo.com", null, NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("email");
    }

    @Test
    void registerRejectsPhoneNotStartingWithThree() {
        assertThatThrownBy(() -> Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", "2001234567", NOW))
                .isInstanceOf(InvalidDriverException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void updateChangesOnlyEditableFields() {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null, NOW);
        OffsetDateTime later = NOW.plusHours(2);

        driver.update("Carlos A. Pérez", "nuevo@correo.com", "3001234567", later);

        assertThat(driver.getFullName()).isEqualTo("Carlos A. Pérez");
        assertThat(driver.getEmail()).isEqualTo("nuevo@correo.com");
        assertThat(driver.getPhone()).isEqualTo("3001234567");
        assertThat(driver.getDocumentNumber()).isEqualTo("1144056789");
        assertThat(driver.getCreatedAt()).isEqualTo(NOW);
        assertThat(driver.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void updateRejectsInvalidDataAndKeepsOriginalValues() {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null, NOW);

        assertThatThrownBy(() -> driver.update("Carlos", "correo-malo", null, NOW.plusHours(1)))
                .isInstanceOf(InvalidDriverException.class);
        assertThat(driver.getEmail()).isEqualTo("carlos@correo.com");
        assertThat(driver.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void statusIsActiveWithoutBlocks() {
        assertThat(restored(false, false).getStatus()).isEqualTo(DriverStatus.ACTIVE);
    }

    @Test
    void statusIsInactiveWhenDocumentsAreExpired() {
        assertThat(restored(true, false).getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
    }

    @Test
    void medicalBlockHasPriorityOverDocumentBlock() {
        assertThat(restored(true, true).getStatus()).isEqualTo(DriverStatus.MEDICAL_HOLD_INCIDENT);
    }

    private static Driver restored(boolean documentBlock, boolean medicalBlock) {
        return Driver.restore(UUID.randomUUID(), "1144056789", "Carlos Pérez", "carlos@correo.com", null,
                documentBlock, medicalBlock, NOW, NOW);
    }
}
