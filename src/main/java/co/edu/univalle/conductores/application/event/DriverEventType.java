package co.edu.univalle.conductores.application.event;

/**
 * Tipos de eventos publicados por Conductores.
 */
public enum DriverEventType {
    DRIVER_ACTIVATED("driver_activated_event");

    private final String value;

    DriverEventType(String value) {
        this.value = value;
    }

    /**
     * Nombre definido en el contrato de eventos.
     *
     * @return nombre externo del evento
     */
    public String value() {
        return value;
    }
}