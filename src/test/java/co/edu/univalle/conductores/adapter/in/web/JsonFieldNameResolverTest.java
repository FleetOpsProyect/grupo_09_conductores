package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class JsonFieldNameResolverTest {

    private static final JsonMapper SNAKE_CASE_MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final JsonFieldNameResolver resolver = new JsonFieldNameResolver(SNAKE_CASE_MAPPER);

    record SampleRequest(String fullName, @JsonProperty("doc_id") String documentNumber, int retryCount) {
    }

    private static SampleRequest sample() {
        return new SampleRequest("Ana", "123", 0);
    }

    @Test
    void translatesFieldWithMapperStrategy() {
        assertThat(resolver.resolve(sample(), "fullName")).isEqualTo("full_name");
    }

    @Test
    void usesJsonPropertyValueWhenPresent() {
        assertThat(resolver.resolve(sample(), "documentNumber")).isEqualTo("doc_id");
    }

    @Test
    void translatesUnknownFieldWithMapperStrategy() {
        assertThat(resolver.resolve(sample(), "otherName")).isEqualTo("other_name");
    }

    @Test
    void returnsNameUnchangedWhenTargetIsNull() {
        assertThat(resolver.resolve(null, "fullName")).isEqualTo("fullName");
    }

    @Test
    void returnsEmptyOrNullNameUnchanged() {
        assertThat(resolver.resolve(sample(), "")).isEmpty();
        assertThat(resolver.resolve(sample(), null)).isNull();
    }

    @Test
    void keepsJavaNameWhenMapperHasNoNamingStrategy() {
        JsonFieldNameResolver plain = new JsonFieldNameResolver(JsonMapper.builder().build());

        assertThat(plain.resolve(sample(), "fullName")).isEqualTo("fullName");
    }

    @Test
    void buildsPathFromJacksonReferences() {
        JacksonException error = catchThrowableOfType(JacksonException.class,
                () -> SNAKE_CASE_MAPPER.readValue("{\"retry_count\":\"abc\"}", SampleRequest.class));

        assertThat(resolver.fromJacksonPath(error.getPath())).isEqualTo("retry_count");
    }

    @Test
    void buildsPathWithIndexFromJacksonReferences() {
        JacksonException error = catchThrowableOfType(JacksonException.class,
                () -> SNAKE_CASE_MAPPER.readValue("[{\"retry_count\":\"abc\"}]", SampleRequest[].class));

        assertThat(resolver.fromJacksonPath(error.getPath())).isEqualTo("[0].retry_count");
    }
}
