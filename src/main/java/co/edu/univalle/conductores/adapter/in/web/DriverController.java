package co.edu.univalle.conductores.adapter.in.web;

import co.edu.univalle.conductores.adapter.in.web.dto.DriverPageResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.DriverResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST para los conductores.
 */
@Tag(name = "Conductores",
    description = "Registro, consulta, listado y actualización de conductores de FleetOps (G09)")
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
    @Operation(summary = "Registrar un conductor (HU-01)",
        description = """
            Crea un conductor con status ACTIVE. El campo status del cuerpo, si llega, se ignora.
            El document_number debe ser único.

            **Fase 2, no implementado:** solo el grupo Administrador de Flota podrá registrar \
            conductores (403 ROLE_NOT_AUTHORIZED). En el MVP cualquier token válido puede hacerlo.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Conductor creado con status ACTIVE"),
        @ApiResponse(responseCode = "400",
            description = """
                INVALID_DATA: uno o más campos no cumplen su regla; errors trae un elemento por \
                cada campo inválido. INVALID_JSON: el cuerpo no es un JSON válido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403",
            description = "ROLE_NOT_AUTHORIZED: fase 2, no implementado en el MVP",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409",
            description = "DUPLICATE_DRIVER: ya existe un conductor con ese document_number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503",
            description = "SERVICE_UNAVAILABLE: la base de datos (Firestore) no responde",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<DriverResponse> register(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader,
            @Valid @RequestBody RegisterDriverRequest request) {
        UUID correlationId = resolveCorrelationId(correlationIdHeader);
        Driver driver = registerDriver.register(new RegisterDriver.Command(
                request.fullName(), request.documentNumber(), request.email(), request.phone(), correlationId));

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Correlation-Id", correlationId.toString())
                .body(DriverResponse.from(driver));
    }

    /**
     * Gets a driver by id. An id that is not a canonical UUID responds 404 DRIVER_NOT_FOUND.
     *
     * @param id driver id as received in the path
     * @return driver found
     */
    @Operation(summary = "Consultar un conductor por id (HU-02)",
        description = """
            Devuelve el conductor con su status actual. Un conductor inactivo \
            (INACTIVE_EXPIRED_DOCUMENTS o MEDICAL_HOLD_INCIDENT) se devuelve con 200, no con 404. \
            Las llamadas internas de otros microservicios, sin JWT, reciben la misma respuesta.

            **Fase 2, no implementado (HU-03, regla "solo el propio"):** si cognito:groups incluye \
            Conductor, el claim custom:driver_id debe ser igual al {id} de la ruta; si es distinto \
            o no viene en el token, se responde 403 ACCESS_DENIED sin consultar la base de datos. \
            En el MVP cualquier token válido consulta cualquier id.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conductor encontrado"),
        @ApiResponse(responseCode = "403",
            description = """
                ACCESS_DENIED: fase 2, no implementado en el MVP. Un conductor intenta consultar \
                un registro que no es el suyo, o su token no trae custom:driver_id.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404",
            description = """
                DRIVER_NOT_FOUND: no existe un conductor con ese id, o el id no es un UUID \
                válido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503",
            description = "SERVICE_UNAVAILABLE: la base de datos (Firestore) no responde",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @Operation(summary = "Listar conductores por estado (HU-06)",
        description = """
            Lista los conductores ordenados de forma ascendente por created_at, con filtro \
            opcional por status. total es el número de conductores que cumplen el filtro en \
            todas las páginas. Si ninguno lo cumple, o page supera la última página, responde \
            200 con content vacío, nunca 404.

            **Fase 2, no implementado:** un token del grupo Conductor recibirá \
            403 ROLE_NOT_AUTHORIZED.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de conductores"),
        @ApiResponse(responseCode = "400",
            description = """
                INVALID_DATA: status, page o size no cumplen su regla; errors trae un elemento \
                por cada parámetro inválido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403",
            description = "ROLE_NOT_AUTHORIZED: fase 2, no implementado en el MVP",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503",
            description = "SERVICE_UNAVAILABLE: la base de datos (Firestore) no responde",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @Operation(summary = "Modificar los datos de un conductor (HU-08)",
        description = """
            Reemplaza los campos editables (full_name, email y phone) con las mismas reglas del \
            registro. El cuerpo debe traer todos los campos obligatorios. El status del conductor \
            no cambia.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conductor actualizado"),
        @ApiResponse(responseCode = "400",
            description = """
                INVALID_DATA: uno o más campos no cumplen su regla (REQUIRED si falta un campo \
                obligatorio); errors trae un elemento por cada campo inválido. INVALID_JSON: el \
                cuerpo no es un JSON válido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404",
            description = """
                DRIVER_NOT_FOUND: no existe un conductor con ese id, o el id no es un UUID \
                válido; no se crea ningún registro.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503",
            description = "SERVICE_UNAVAILABLE: la base de datos (Firestore) no responde",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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

    private static UUID resolveCorrelationId(String correlationIdHeader) {
        if (correlationIdHeader == null || !UUID_PATTERN.matcher(correlationIdHeader).matches()) {
            return UUID.randomUUID();
        }
        return UUID.fromString(correlationIdHeader);
    }
}
