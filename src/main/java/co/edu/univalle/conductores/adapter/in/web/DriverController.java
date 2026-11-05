package co.edu.univalle.conductores.adapter.in.web;

import co.edu.univalle.conductores.adapter.in.web.dto.DriverPageResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.DriverResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.RegisterDriverRequest;
import co.edu.univalle.conductores.adapter.in.web.dto.UpdateDriverRequest;
import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PageResult;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST para los conductores.
 */
@RestController
@RequestMapping("/drivers")
public class DriverController {

    /** Strict canonical UUID: 8-4-4-4-12 hexadecimal digits. */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final RegisterDriver registerDriver;
    private final GetDriver getDriver;
    private final ListDriver listDriver;
    private final UpdateDriver updateDriver;

    /**
     * Crea el controlador con los puertos de entrada.
     *
     * @param registerDriver caso de uso de registro
     * @param getDriver caso de uso de consulta
     * @param listDriver caso de uso de listado
     * @param updateDriver caso de uso de actualización
     */
    public DriverController(RegisterDriver registerDriver, GetDriver getDriver,
            ListDriver listDriver, UpdateDriver updateDriver) {
        this.registerDriver = registerDriver;
        this.getDriver = getDriver;
        this.listDriver = listDriver;
        this.updateDriver = updateDriver;
    }

    /**
     * Registra un conductor.
     *
     * @param request datos del conductor
     * @return conductor creado con código 201
     */
    @PostMapping
    public ResponseEntity<DriverResponse> register(@Valid @RequestBody RegisterDriverRequest request) {
        Driver driver = registerDriver.register(new RegisterDriver.Command(
                request.fullName(), request.documentNumber(), request.email(), request.phone()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DriverResponse.from(driver));
    }

    /**
     * Gets a driver by id. An id that is not a canonical UUID responds 404 DRIVER_NOT_FOUND.
     *
     * @param id driver id as received in the path
     * @return driver found
     */
    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable String id) {
        return DriverResponse.from(getDriver.get(parseId(id)));
    }

    /**
     * Lista conductores con filtro opcional por estado y paginación.
     *
     * @param status estado a filtrar, opcional
     * @param page número de página desde 0
     * @param size tamaño de página, entre 1 y 100
     * @return página de conductores
     */
    @GetMapping
    public DriverPageResponse list(@RequestParam(required = false) DriverStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<Driver> result = listDriver.list(new ListDriver.Query(status, page, size));
        return new DriverPageResponse(
                result.content().stream().map(DriverResponse::from).toList(),
                result.page(), result.size(), result.total());
    }

    /**
     * Updates the editable data of a driver. The body is validated first (400 INVALID_DATA);
     * then an id that is not a canonical UUID responds 404 DRIVER_NOT_FOUND.
     *
     * @param id driver id as received in the path
     * @param request new data
     * @return updated driver
     */
    @PutMapping("/{id}")
    public DriverResponse update(@PathVariable String id, @Valid @RequestBody UpdateDriverRequest request) {
        Driver driver = updateDriver.update(parseId(id), new UpdateDriver.Command(
                request.fullName(), request.email(), request.phone()));
        return DriverResponse.from(driver);
    }

    /**
     * Converts the path id to a UUID. A value that is not a canonical UUID cannot match any driver,
     * so it is reported as not found with the original value.
     *
     * @param id id as received
     * @return parsed UUID
     * @throws DriverNotFoundException when the id is not a canonical UUID
     */
    private static UUID parseId(String id) {
        if (!UUID_PATTERN.matcher(id).matches()) {
            throw new DriverNotFoundException(id);
        }
        return UUID.fromString(id);
    }
}
