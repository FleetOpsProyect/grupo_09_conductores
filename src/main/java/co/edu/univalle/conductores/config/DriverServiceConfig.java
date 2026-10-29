package co.edu.univalle.conductores.config;

import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.application.service.GetDriverService;
import co.edu.univalle.conductores.application.service.ListDriverService;
import co.edu.univalle.conductores.application.service.RegisterDriverService;
import co.edu.univalle.conductores.application.service.UpdateDriverService;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conecta los casos de uso de conductores con sus puertos.
 */
@Configuration
public class DriverServiceConfig {

    /**
     * Reloj en la zona horaria del proyecto (GMT-5).
     *
     * @return reloj del sistema en America/Bogota
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }

    /**
     * Caso de uso de registro.
     *
     * @param repository repositorio de conductores
     * @param clock reloj del proyecto
     * @return implementación de RegisterDriver
     */
    @Bean
    public RegisterDriver registerDriver(DriverRepository repository, Clock clock) {
        return new RegisterDriverService(repository, clock);
    }

    /**
     * Caso de uso de consulta por id.
     *
     * @param repository repositorio de conductores
     * @return implementación de GetDriver
     */
    @Bean
    public GetDriver getDriver(DriverRepository repository) {
        return new GetDriverService(repository);
    }

    /**
     * Caso de uso de listado.
     *
     * @param repository repositorio de conductores
     * @return implementación de ListDriver
     */
    @Bean
    public ListDriver listDriver(DriverRepository repository) {
        return new ListDriverService(repository);
    }

    /**
     * Caso de uso de actualización.
     *
     * @param repository repositorio de conductores
     * @param clock reloj del proyecto
     * @return implementación de UpdateDriver
     */
    @Bean
    public UpdateDriver updateDriver(DriverRepository repository, Clock clock) {
        return new UpdateDriverService(repository, clock);
    }
}
