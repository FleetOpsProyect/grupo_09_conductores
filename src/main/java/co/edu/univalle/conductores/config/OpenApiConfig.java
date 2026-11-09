@Configuration
public class OpenApiCOnfig {
    @Bean
    public OpenAPI fleetOpsOpenApi() {
        return new OpenAPI()
        .info(new info()
            .title("FleetOps - Microservicio de Conductores (G09)")
            .version("1.0.0")
            .description("""MVP: el API Gateway valida el JWT de Cognito (401 si falta o es inválido).
                        Cualquier token válido actúa como superusuario; el servicio no responde 403.
                        Las llamadas internas llegan sin JWT, protegidas por security group.
                        Fase 2 (documentada, no implementada): roles con cognito:groups y regla
                        "solo el propio" con custom:driver_id."""))
        .components(new Components().addSecuritySchemes("bearerAuth",
            new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .scheme("bearer").type(SecurityScheme.Type.HTTP)))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}