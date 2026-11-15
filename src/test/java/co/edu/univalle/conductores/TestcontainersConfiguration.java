package co.edu.univalle.conductores;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Contenedores auxiliares de las pruebas de integración.
 *
 * <p>LocalStack no se registra aquí: solo lo usa
 * {@code DriverEventListenerLocalStackIntegrationTest}, que levanta su propio contenedor y apunta
 * {@code spring.cloud.aws.endpoint} a él.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

}
