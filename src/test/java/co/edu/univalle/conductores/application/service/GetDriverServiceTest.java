package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDriverServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T16:00:00-05:00");

    @Mock
    private DriverRepository repository;

    @Test
    void getReturnsDriverWhenItExists() {
        UUID id = UUID.randomUUID();
        Driver driver = Driver.restore(id, "1144056789", "Carlos Pérez", "carlos@correo.com", null,
                false, false, NOW, NOW);
        when(repository.findById(id)).thenReturn(Optional.of(driver));

        Driver result = new GetDriverService(repository).get(id);

        assertThat(result).isSameAs(driver);
    }

    @Test
    void getThrowsWhenDriverDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetDriverService(repository).get(id))
                .isInstanceOf(DriverNotFoundException.class);
    }
}
