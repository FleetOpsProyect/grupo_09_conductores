package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateDriverServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T21:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private DriverRepository repository;

    @Test
    void updateChangesDriverAndSavesIt() {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null,
                OffsetDateTime.now(CLOCK));
        UUID id = driver.getId();
        when(repository.findById(id)).thenReturn(Optional.of(driver));

        Driver result = new UpdateDriverService(repository, CLOCK)
                .update(id, new UpdateDriver.Command("Carlos A. Pérez", "nuevo@correo.com", "3001234567"));

        assertThat(result.getFullName()).isEqualTo("Carlos A. Pérez");
        assertThat(result.getEmail()).isEqualTo("nuevo@correo.com");
        verify(repository).save(driver);
    }

    @Test
    void updateThrowsWhenDriverDoesNotExistAndSavesNothing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UpdateDriverService(repository, CLOCK)
                .update(id, new UpdateDriver.Command("Carlos Pérez", "carlos@correo.com", null)))
                .isInstanceOf(DriverNotFoundException.class);
        verify(repository, never()).save(any(Driver.class));
    }
}
