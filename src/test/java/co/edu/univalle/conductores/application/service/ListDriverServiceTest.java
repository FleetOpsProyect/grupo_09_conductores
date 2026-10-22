package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListDriverServiceTest {

    @Mock
    private DriverRepository repository;

    @Test
    void listPassesStatusAndPagingToRepository() {
        PageResult<Driver> page = new PageResult<>(List.of(), 1, 10, 0);
        when(repository.findAll(DriverStatus.ACTIVE, 1, 10)).thenReturn(page);

        PageResult<Driver> result =
                new ListDriverService(repository).list(new ListDriver.Query(DriverStatus.ACTIVE, 1, 10));

        assertThat(result).isSameAs(page);
    }

    @Test
    void listWithoutStatusQueriesAllDrivers() {
        PageResult<Driver> page = new PageResult<>(List.of(), 0, 20, 0);
        when(repository.findAll(null, 0, 20)).thenReturn(page);

        PageResult<Driver> result = new ListDriverService(repository).list(new ListDriver.Query(null, 0, 20));

        assertThat(result).isSameAs(page);
    }
}
