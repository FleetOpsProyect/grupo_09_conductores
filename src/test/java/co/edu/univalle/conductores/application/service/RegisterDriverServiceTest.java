package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.DuplicateDocumentException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterDriverServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T21:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private DriverRepository repository;

    private RegisterDriverService service;

    @BeforeEach
    void setUp() {
        service = new RegisterDriverService(repository, CLOCK);
    }

    @Test
    void registerSavesActiveDriverWithUniquenessAndReturnsIt() {
        RegisterDriver.Command command = new RegisterDriver.Command("Carlos Pérez", "1144056789",
                "carlos@correo.com", null);

        Driver result = service.register(command);

        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(repository).createWithUniqueness(captor.capture());
        assertThat(captor.getValue()).isSameAs(result);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(result.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-10-22T16:00:00-05:00"));
    }

    @Test
    void registerPropagatesDuplicateDocument() {
        RegisterDriver.Command command = new RegisterDriver.Command("Carlos Pérez", "1144056789",
                "carlos@correo.com", null);
        doThrow(new DuplicateDocumentException("1144056789")).when(repository).createWithUniqueness(any(Driver.class));

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(DuplicateDocumentException.class);
    }
}
