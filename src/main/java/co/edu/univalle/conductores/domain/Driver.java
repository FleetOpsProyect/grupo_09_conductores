package co.edu.univalle.conductores.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * Conductor: agregado raíz del microservicio. Su estado se calcula a partir de los bloqueos internos.
 */
@Getter
public class Driver {

    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("^[0-9]{6,10}$");
    private static final Pattern PHONE = Pattern.compile("^3[0-9]{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?\\.[a-zA-Z]{2,}$");
    private static final int MAX_EMAIL_LENGTH = 100;

    private final UUID id;
    private final String documentNumber;
    private String fullName;
    private String email;
    private String phone;
    private boolean documentBlock;
    private boolean medicalBlock;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private Driver(UUID id, String documentNumber, String fullName, String email, String phone,
            boolean documentBlock, boolean medicalBlock, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.documentNumber = documentNumber;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.documentBlock = documentBlock;
        this.medicalBlock = medicalBlock;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Registra un conductor nuevo. Nace sin bloqueos, así que su estado inicial es ACTIVE.
     *
     * @param fullName nombre completo
     * @param documentNumber número de documento (solo dígitos)
     * @param email correo electrónico
     * @param phone teléfono opcional de 10 dígitos que empieza por 3
     * @param now momento de registro
     * @return el conductor nuevo
     */
    public static Driver register(String fullName, String documentNumber, String email, String phone,
            OffsetDateTime now) {
        if (documentNumber == null || !DOCUMENT_NUMBER.matcher(documentNumber).matches()) {
            throw new InvalidDriverException("document_number debe tener entre 6 y 10 dígitos");
        }
        validateEditableFields(fullName, email, phone);
        return new Driver(UUID.randomUUID(), documentNumber, fullName.trim(), email.trim(), phone,
                false, false, now, now);
    }

    /**
     * Reconstruye un conductor desde su almacenamiento. Lo usa solo el adaptador de persistencia.
     *
     * @param id identificador existente
     * @param documentNumber número de documento
     * @param fullName nombre completo
     * @param email correo electrónico
     * @param phone teléfono opcional
     * @param documentBlock bloqueo por documentos vencidos
     * @param medicalBlock bloqueo por incidente grave
     * @param createdAt fecha de creación
     * @param updatedAt fecha de última modificación
     * @return el conductor reconstruido
     */
    public static Driver restore(UUID id, String documentNumber, String fullName, String email, String phone,
            boolean documentBlock, boolean medicalBlock, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Driver(id, documentNumber, fullName, email, phone,
                documentBlock, medicalBlock, createdAt, updatedAt);
    }

    /**
     * Cambia solo los datos editables. El número de documento y el estado no cambian aquí.
     *
     * @param fullName nombre completo
     * @param email correo electrónico
     * @param phone teléfono opcional
     * @param now momento de la modificación
     */
    public void update(String fullName, String email, String phone, OffsetDateTime now) {
        validateEditableFields(fullName, email, phone);
        this.fullName = fullName.trim();
        this.email = email.trim();
        this.phone = phone;
        this.updatedAt = now;
    }

    /**
     * Estado calculado con prioridad médico > documentos > activo.
     *
     * @return el estado actual del conductor
     */
    public DriverStatus getStatus() {
        if (medicalBlock) {
            return DriverStatus.MEDICAL_HOLD_INCIDENT;
        }
        if (documentBlock) {
            return DriverStatus.INACTIVE_EXPIRED_DOCUMENTS;
        }
        return DriverStatus.ACTIVE;
    }

    private static void validateEditableFields(String fullName, String email, String phone) {
        if (fullName == null || fullName.trim().length() < 3 || fullName.trim().length() > 100) {
            throw new InvalidDriverException("full_name debe tener entre 3 y 100 caracteres");
        }
        if (email == null || email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email.trim()).matches()) {
            throw new InvalidDriverException("email no es válido");
        }
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new InvalidDriverException("phone debe tener 10 dígitos y empezar por 3");
        }
    }
}
