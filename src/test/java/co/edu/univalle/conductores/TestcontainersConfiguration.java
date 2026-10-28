package co.edu.univalle.conductores;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Contenedores auxiliares de las pruebas de integración.
 *
 * <p>Cuando se añadan las pruebas de mensajería SNS/SQS se debe registrar aquí un
 * {@code GenericContainer} con la imagen {@code localstack/localstack} exponiendo SNS y SQS,
 * y apuntar {@code spring.cloud.aws.endpoint} a la URL del contenedor.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

}
