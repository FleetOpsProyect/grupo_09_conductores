package co.edu.univalle.conductores.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la especificación OpenAPI y esquema de seguridad Bearer (JWT de Cognito).
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad Bearer en la especificación. */
    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * Define el título, la versión, la descripción de seguridad y el esquema Bearer de la API.
     *
     * @return especificación OpenAPI base
     */
    @Bean
    public OpenAPI fleetOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FleetOps - Microservicio de Conductores (G09)")
                        .version("1.0.0")
                        .description("""
                                Seguridad: el authorizer fleetops-authorizer de API Gateway verifica el \
                                ID token de Cognito (firma, emisor, audiencia y expiración) y aplica los \
                                permisos por grupo de Cognito antes de que la petición llegue al \
                                servicio. Sin encabezado Authorization responde 401; con un token \
                                inválido o expirado, o con un grupo no permitido, responde 403. Esas \
                                respuestas las genera el Gateway con el cuerpo {"message": ...} y no \
                                siguen el formato común de error. El servicio no valida roles.

                                Permisos: FLEET_ADMIN puede usar todas las rutas de conductores; DRIVER \
                                solo GET /drivers/{id} con su propio id (custom:driver_id); los demás \
                                grupos no tienen acceso. Las llamadas internas de otros microservicios \
                                llegan directas a la EC2, sin JWT, y no pasan por el authorizer.

                                Trazabilidad: todas las rutas aceptan el encabezado opcional \
                                X-Correlation-Id (UUID). Si no llega o no es un UUID válido, el \
                                servicio genera uno. Siempre se devuelve en el encabezado \
                                X-Correlation-Id de la respuesta, también en los errores, y aparece \
                                en los logs y en los eventos publicados."""))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
