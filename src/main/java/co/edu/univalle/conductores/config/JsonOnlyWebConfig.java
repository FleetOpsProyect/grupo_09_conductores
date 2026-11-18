package co.edu.univalle.conductores.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps the HTTP API JSON only.
 *
 * <p>firebase-admin brings jackson-dataformat-xml as a transitive dependency, and Spring MVC registers an
 * XML converter whenever it finds it. That converter does not use the snake_case JSON configuration, so a
 * client asking for {@code Accept: application/xml} would get an undocumented XML body. Removing every
 * XML converter makes that request end in 406 NOT_ACCEPTABLE with the common error format (TEC_04).
 */
@Configuration(proxyBeanMethods = false)
public class JsonOnlyWebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> converters.removeIf(JsonOnlyWebConfig::writesXml));
    }

    /**
     * Tells whether a converter handles an XML media type (application/xml, text/xml or any +xml type).
     *
     * @param converter converter to check
     * @return true when it reads or writes XML
     */
    static boolean writesXml(HttpMessageConverter<?> converter) {
        return converter.getSupportedMediaTypes().stream().anyMatch(JsonOnlyWebConfig::isXml);
    }

    private static boolean isXml(MediaType type) {
        return "xml".equalsIgnoreCase(type.getSubtype()) || "xml".equalsIgnoreCase(type.getSubtypeSuffix());
    }
}
