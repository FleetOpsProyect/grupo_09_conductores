package co.edu.univalle.conductores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio de Conductores (FleetOps, Grupo 09).
 */
@SpringBootApplication
public class MsConductoresApplication {

    /**
     * Arranca la aplicación Spring Boot.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(MsConductoresApplication.class, args);
    }

}
