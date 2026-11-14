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
                                MVP: el API Gateway valida el JWT de Cognito y responde 401 si falta \
                                o es inválido. Cualquier token válido actúa como superusuario; el \
                                servicio no valida roles y no responde 403. Las llamadas internas \
                                de otros microservicios llegan sin JWT, protegidas por security group.

                                Fase 2 (documentada, no implementada): validación de roles con \
                                cognito:groups y regla "solo el propio" con custom:driver_id, \
                                con respuesta 403."""))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
