package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web slice tests for unknown routes, unsupported methods and unexpected errors (TEC_04-2).
 */
@WebMvcTest(DriverController.class)
@Import(JsonFieldNameResolver.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerWebTest {

    private static final List<String> COMMON_KEYS = List.of("code", "message", "timestamp", "path");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private RegisterDriver registerDriver;

    @MockitoBean
    private GetDriver getDriver;

    @MockitoBean
    private ListDriver listDriver;

    @MockitoBean
    private UpdateDriver updateDriver;

    @Test
    void sliceUsesSnakeCaseNaming() {
        assertThat(jsonMapper.serializationConfig().getPropertyNamingStrategy())
                .isEqualTo(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Test
    void unknownRouteReturns404RouteNotFound() throws Exception {
        MockHttpServletResponse response = perform(get("/does_not_exist"));

        assertThat(response.getStatus()).isEqualTo(404);
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertCommonFormat(body);
        assertThat(body.get("code").asString()).isEqualTo("ROUTE_NOT_FOUND");
        assertThat(body.get("message").asString()).isEqualTo("Ruta no encontrada");
        assertThat(body.get("path").asString()).isEqualTo("/does_not_exist");
        assertThat(OffsetDateTime.parse(body.get("timestamp").asString()).getOffset().getId())
                .isEqualTo("-05:00");
    }

    @Test
    void deleteDriversReturns405MethodNotAllowed() throws Exception {
        MockHttpServletResponse response = perform(delete("/drivers"));

        assertThat(response.getStatus()).isEqualTo(405);
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertCommonFormat(body);
        assertThat(body.get("code").asString()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(body.get("message").asString()).isEqualTo("Método no permitido para esta ruta");
        assertThat(body.get("path").asString()).isEqualTo("/drivers");
        assertThat(response.getHeader(HttpHeaders.ALLOW)).contains("GET", "POST");
    }

    @Test
    void unexpectedExceptionReturns500WithoutDetailsAndLogsTrace(CapturedOutput output) throws Exception {
        when(listDriver.list(any())).thenThrow(new RuntimeException("secret detail"));

        MockHttpServletResponse response = perform(get("/drivers"));

        assertThat(response.getStatus()).isEqualTo(500);
        String raw = response.getContentAsString();
        JsonNode body = jsonMapper.readTree(raw);
        assertCommonFormat(body);
        assertThat(body.get("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("message").asString()).isEqualTo("Error interno del servidor");
        assertThat(body.get("path").asString()).isEqualTo("/drivers");
        assertThat(raw).doesNotContain("secret detail", "RuntimeException", "at co.edu");
        assertThat(output.getOut())
                .contains("ERROR")
                .contains("Unexpected error on GET /drivers")
                .contains("java.lang.RuntimeException: secret detail")
                .contains("\tat ");
    }

    @Test
    void unsupportedMediaTypeKeepsSpringStatus() throws Exception {
        MockHttpServletResponse response = perform(post("/drivers")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"));

        assertThat(response.getStatus()).isEqualTo(415);
    }

    private MockHttpServletResponse perform(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static void assertCommonFormat(JsonNode body) {
        List<String> keys = new ArrayList<>(body.propertyNames());
        assertThat(keys).containsExactlyInAnyOrderElementsOf(COMMON_KEYS);
        assertThat(body.has("status")).isFalse();
        assertThat(body.has("error")).isFalse();
    }
}
