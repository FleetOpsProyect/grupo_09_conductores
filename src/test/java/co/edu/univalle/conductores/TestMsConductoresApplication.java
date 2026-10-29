package co.edu.univalle.conductores;

import org.springframework.boot.SpringApplication;

public class TestMsConductoresApplication {

    /**
     * Arranca la aplicación con los contenedores de prueba.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.from(MsConductoresApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args);
    }

}
