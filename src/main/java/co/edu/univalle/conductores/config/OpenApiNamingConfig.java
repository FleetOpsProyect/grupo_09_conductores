package co.edu.univalle.conductores.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura los esquemas OpenAPI con los nombres de propiedad del JSON de la API.
 */
@Configuration
public class OpenApiNamingConfig {

    /**
     * Resuelve los modelos usando snake_case.
     *
     * @param properties propiedades de springdoc
     * @return resolvedor de modelos con nombres snake_case
     */
    @Bean
    public ModelResolver snakeCaseModelResolver(SpringDocConfigProperties properties) {
        ObjectMapper mapper = (properties.isOpenapi31() ? Json31.mapper() : Json.mapper()).copy();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper).openapi31(properties.isOpenapi31());
    }
}
