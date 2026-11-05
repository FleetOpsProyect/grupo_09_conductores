package co.edu.univalle.conductores.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the Java name of a request field reported by bean validation (for example "fullName")
 * into the JSON name clients send (for example "full_name").
 *
 * <p>Uses the field's {@link JsonProperty} value when present, otherwise the naming
 * strategy configured on the application's {@link JsonMapper}.
 */
@Component
public class JsonFieldNameResolver {

    private final JsonMapper jsonMapper;

    /**
     * Creates the resolver.
     *
     * @param jsonMapper the mapper used to read request bodies
     */
    public JsonFieldNameResolver(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Resolves the JSON name of a field of the validated object.
     *
     * @param target the validated object, may be null
     * @param javaName field name as reported by bean validation
     * @return the JSON name, or {@code javaName} unchanged when {@code target} is null
     */
    public String resolve(Object target, String javaName) {
        if (target == null || javaName == null || javaName.isEmpty()) {
            return javaName;
        }
        JsonProperty property = jsonProperty(target.getClass(), javaName);
        if (property != null && !property.value().isEmpty()) {
            return property.value();
        }
        DeserializationConfig config = jsonMapper.deserializationConfig();
        PropertyNamingStrategy strategy = config.getPropertyNamingStrategy();
        return strategy == null ? javaName : strategy.nameForField(config, null, javaName);
    }

    /**
     * Builds a path from a Jackson error, whose references already carry JSON names.
     *
     * @param path references from {@link JacksonException#getPath()}
     * @return the JSON path, for example "full_name"
     */
    public String fromJacksonPath(List<JacksonException.Reference> path) {
        StringBuilder result = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            if (reference.getPropertyName() != null) {
                if (!result.isEmpty()) {
                    result.append('.');
                }
                result.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                result.append('[').append(reference.getIndex()).append(']');
            }
        }
        return result.toString();
    }

    private static JsonProperty jsonProperty(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name).getAnnotation(JsonProperty.class);
        } catch (NoSuchFieldException ex) {
            return null;
        }
    }
}
