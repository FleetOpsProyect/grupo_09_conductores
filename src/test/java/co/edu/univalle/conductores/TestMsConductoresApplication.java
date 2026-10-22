package co.edu.univalle.conductores;

import org.springframework.boot.SpringApplication;

public class TestMsConductoresApplication {

	public static void main(String[] args) {
		SpringApplication.from(MsConductoresApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
