package co.edu.univalle.conductores.adapter.in.web;

import co.edu.univalle.conductores.adapter.in.web.dto.DriverPageResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.DriverResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.GatewayErrorResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.MedicalReleaseRequest;
import co.edu.univalle.conductores.adapter.in.web.dto.RegisterDriverRequest;
import co.edu.univalle.conductores.adapter.in.web.dto.UpdateDriverRequest;
import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.ReleaseMedicalBlock;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PageResult;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
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

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    /** Strict canonical UUID: 8-4-4-4-12 hexadecimal digits. */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Role rule of the routes that only FLEET_ADMIN can call through API Gateway. */
    static final String FLEET_ADMIN_ONLY = """
            **Roles:** por API Gateway solo el grupo FLEET_ADMIN (Administrador de Flota). Lo aplica \
            el authorizer del Gateway; el servicio no valida roles. Las llamadas internas de otros \
            microservicios llegan directas a la EC2, sin JWT, y no pasan por el authorizer.""";

    /** 401 returned by API Gateway, not by the service. */
    static final String GATEWAY_401 = """
            Lo responde API Gateway, no el servicio: falta el encabezado Authorization. Cuerpo \
            {"message": "Unauthorized"}, que no sigue el formato común de error.""";

    /** 403 returned by API Gateway for routes that only FLEET_ADMIN can call. */
    static final String GATEWAY_403_FLEET_ADMIN = """
            Lo responde API Gateway, no el servicio: el token es inválido o expiró, o cognito:groups \
            no incluye FLEET_ADMIN. Cuerpo {"message": "Forbidden"}, que no sigue el formato común de \
            error.""";

    private final RegisterDriver registerDriver;
    private final GetDriver getDriver;
    private final ListDriver listDriver;
    private final UpdateDriver updateDriver;
    private final ReleaseMedicalBlock releaseMedicalBlock;

    /**
     * Crea el controlador con los puertos de entrada.
     *
     * @param registerDriver caso de uso de registro
     * @param getDriver caso de uso de consulta
     * @param listDriver caso de uso de listado
     * @param updateDriver caso de uso de actualización
     * @param releaseMedicalBlock caso de uso de alta médica
     */
    public DriverController(RegisterDriver registerDriver, GetDriver getDriver,
            ListDriver listDriver, UpdateDriver updateDriver, ReleaseMedicalBlock releaseMedicalBlock) {
        this.registerDriver = registerDriver;
        this.getDriver = getDriver;
        this.listDriver = listDriver;
        this.updateDriver = updateDriver;
        this.releaseMedicalBlock = releaseMedicalBlock;
    }

    /**
     * Registra un conductor.
     *
     * @param correlationId correlation_id resuelto por {@link CorrelationIdFilter}
     * @param request datos del conductor
     * @return conductor creado con código 201
     */
    @Operation(summary = "Registrar un conductor (HU-01)",
        description = """
            Crea un conductor con status ACTIVE. El campo status del cuerpo, si llega, se ignora.
            El document_number debe ser único.

            """ + FLEET_ADMIN_ONLY)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Conductor creado con status ACTIVE"),
        @ApiResponse(responseCode = "400",
            description = """
                INVALID_DATA: uno o más campos no cumplen su regla; errors trae un elemento por \
                cada campo inválido. INVALID_JSON: el cuerpo no es un JSON válido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = GATEWAY_401,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = GATEWAY_403_FLEET_ADMIN,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
        @ApiResponse(responseCode = "409",
            description = "DUPLICATE_DRIVER: ya existe un conductor con ese document_number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503",
            description = "SERVICE_UNAVAILABLE: la base de datos (Firestore) no responde",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<DriverResponse> register(
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) UUID correlationId,
            @Valid @RequestBody RegisterDriverRequest request) {
        Driver driver = registerDriver.register(new RegisterDriver.Command(
                request.fullName(), request.documentNumber(), request.email(), request.phone(), correlationId));

        return ResponseEntity.status(HttpStatus.CREATED).body(DriverResponse.from(driver));
    }

    /**
     * Gets a driver by id. An id that is not a canonical UUID responds 404 DRIVER_NOT_FOUND.
     *
     * @param id driver id as received in the path
     * @return driver found
     */
    @Operation(summary = "Consultar un conductor por id (HU-02, HU-03)",
        description = """
            Devuelve el conductor con su status actual. Un conductor inactivo \
            (INACTIVE_EXPIRED_DOCUMENTS o MEDICAL_HOLD_INCIDENT) se devuelve con 200, no con 404. \
            Las llamadas internas de otros microservicios, sin JWT, reciben la misma respuesta.

            **Roles (HU-03, regla "solo el propio"):** por API Gateway, FLEET_ADMIN consulta \
            cualquier id; DRIVER solo el suyo, es decir, cuando el claim custom:driver_id de su ID \
            token es igual al {id} de la ruta. Lo aplica el authorizer del Gateway: si el id no es el \
            suyo o el token no trae custom:driver_id, responde 403 sin que la petición llegue al \
            servicio, así que la respuesta no revela si ese id existe. El servicio no valida roles.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conductor encontrado"),
        @ApiResponse(responseCode = "401", description = GATEWAY_401,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
        @ApiResponse(responseCode = "403",
            description = """
                Lo responde API Gateway, no el servicio: el token es inválido o expiró; el grupo no \
                es FLEET_ADMIN ni DRIVER; o es DRIVER y custom:driver_id falta o no es igual al {id}. \
                Cuerpo {"message": "Forbidden"}, que no sigue el formato común de error.""",
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
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

            """ + FLEET_ADMIN_ONLY)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de conductores"),
        @ApiResponse(responseCode = "400",
            description = """
                INVALID_DATA: status, page o size no cumplen su regla; errors trae un elemento \
                por cada parámetro inválido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = GATEWAY_401,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = GATEWAY_403_FLEET_ADMIN,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
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
     * @param correlationId correlation_id resolved by {@link CorrelationIdFilter}
     * @param request new data
     * @return updated driver
     */
    @Operation(summary = "Modificar los datos de un conductor (HU-08)",
        description = """
            Modifica los campos editables (full_name, email y phone) con las mismas reglas del \
            registro. El cuerpo debe traer los obligatorios, full_name y email; si falta uno, \
            responde 400 con REQUIRED para ese campo. phone es opcional: si la clave no llega, se \
            mantiene el teléfono guardado; si llega "phone": null, se borra; si llega un valor, se \
            reemplaza. document_number no es editable: puede llegar con el valor guardado, pero \
            con otro valor responde 400. El status del conductor no cambia y, si llega en el \
            cuerpo, se ignora. Si al menos un campo cambia, se guarda driver_updated_event en el \
            outbox en la misma transacción; si nada cambia, no se genera evento.

            """ + FLEET_ADMIN_ONLY)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conductor actualizado"),
        @ApiResponse(responseCode = "400",
            description = """
                INVALID_DATA: uno o más campos no cumplen su regla (REQUIRED si falta un campo \
                obligatorio); errors trae un elemento por cada campo inválido. Si el resto del \
                cuerpo es válido y document_number llega con un valor distinto al guardado, errors \
                trae {"field": "document_number", "code": "NOT_EDITABLE"}. INVALID_JSON: el \
                cuerpo no es un JSON válido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = GATEWAY_401,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = GATEWAY_403_FLEET_ADMIN,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
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
    public DriverResponse update(@PathVariable String id,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) UUID correlationId,
            @Valid @RequestBody UpdateDriverRequest request) {
        Driver driver = updateDriver.update(parseId(id), new UpdateDriver.Command(
                request.getFullName(), request.getEmail(), request.isPhonePresent(), request.getPhone(),
                request.getDocumentNumber(), correlationId));
        return DriverResponse.from(driver);
    }

    /**
     * Registra el alta médica de un conductor.
     *
     * @param id identificador del conductor
     * @param correlationId correlation_id resuelto por {@link CorrelationIdFilter}
     * @param request datos opcionales del alta médica
     * @return conductor actualizado con código 200
     */
    @Operation(summary = "Registrar alta médica de un conductor (HU-10)",
        description = """
            Registra el alta médica de un conductor para rehabilitar su estado operativo. \
            Si el conductor no tiene bloqueo por documentos vencidos, queda en estado ACTIVE \
            y se genera el evento driver_activated_event. Si tiene bloqueo por documentos \
            vencidos, queda en estado INACTIVE_EXPIRED_DOCUMENTS y no se genera evento. \
            El cuerpo es opcional y permite registrar notas médicas de hasta 500 caracteres.

            """ + FLEET_ADMIN_ONLY,
        parameters = {
            @Parameter(name = "id", in = ParameterIn.PATH, required = true,
                description = "Identificador del conductor (UUID canónico de 36 caracteres)",
                schema = @Schema(type = "string", format = "uuid")),
            @Parameter(name = "X-Correlation-Id", in = ParameterIn.HEADER, required = false,
                description = "Identificador de correlación opcional (UUID)",
                schema = @Schema(type = "string", format = "uuid"))
        })
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alta médica registrada exitosamente",
            content = @Content(schema = @Schema(implementation = DriverResponse.class))),
        @ApiResponse(responseCode = "400",
            description = """
                INVALID_DATA: las notas superan el límite permitido (máximo 500 caracteres). \
                errors trae {"field": "notes", "code": "INVALID_LENGTH"}. \
                INVALID_JSON: el cuerpo no es un JSON válido.""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "INVALID_LENGTH",
                    value = """
                        {\
                          "code": "INVALID_DATA",\
                          "message": "Los datos del conductor no son válidos",\
                          "timestamp": "2026-11-23T00:00:00-05:00",\
                          "path": "/drivers/7b0e4a52-2f1c-4c55-9d3e-1a2b3c4d5e6f/medical-release",\
                          "errors": [{"field": "notes", "code": "INVALID_LENGTH"}]\
                        }"""))),
        @ApiResponse(responseCode = "401", description = GATEWAY_401,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = GATEWAY_403_FLEET_ADMIN,
            content = @Content(schema = @Schema(implementation = GatewayErrorResponse.class))),
        @ApiResponse(responseCode = "404",
            description = "DRIVER_NOT_FOUND: no existe un conductor con ese id o el id no es un UUID válido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "DRIVER_NOT_FOUND",
                    value = """
                        {\
                          "code": "DRIVER_NOT_FOUND",\
                          "message": "No existe un conductor con id '7b0e4a52-2f1c-4c55-9d3e-1a2b3c4d5e6f'",\
                          "timestamp": "2026-11-23T00:00:00-05:00",\
                          "path": "/drivers/7b0e4a52-2f1c-4c55-9d3e-1a2b3c4d5e6f/medical-release",\
                          "errors": []\
                        }"""))),
        @ApiResponse(responseCode = "409",
            description = "STATUS_NOT_ALLOWED: el conductor no está inhabilitado por incidente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "STATUS_NOT_ALLOWED",
                    value = """
                        {\
                          "code": "STATUS_NOT_ALLOWED",\
                          "message": "El conductor no está inhabilitado por incidente",\
                          "timestamp": "2026-11-23T00:00:00-05:00",\
                          "path": "/drivers/7b0e4a52-2f1c-4c55-9d3e-1a2b3c4d5e6f/medical-release",\
                          "errors": []\
                        }"""))),
        @ApiResponse(responseCode = "503",
            description = "SERVICE_UNAVAILABLE: la base de datos (Firestore) no responde",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "SERVICE_UNAVAILABLE",
                    value = """
                        {\
                          "code": "SERVICE_UNAVAILABLE",\
                          "message": "Servicio temporalmente no disponible, intente nuevamente",\
                          "timestamp": "2026-11-23T00:00:00-05:00",\
                          "path": "/drivers/7b0e4a52-2f1c-4c55-9d3e-1a2b3c4d5e6f/medical-release",\
                          "errors": []\
                        }""")))
    })
    @PostMapping("/{id}/medical-release")
    public ResponseEntity<DriverResponse> releaseMedical(
            @PathVariable String id,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) UUID correlationId,
            @Valid @RequestBody(required = false) MedicalReleaseRequest request) {
        String notes = request != null ? request.notes() : null;
        Driver driver = releaseMedicalBlock.release(parseId(id),
                new ReleaseMedicalBlock.Command(notes, correlationId));
        log.info("Alta médica registrada para el conductor {}", driver.getId());
        return ResponseEntity.ok(DriverResponse.from(driver));
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
