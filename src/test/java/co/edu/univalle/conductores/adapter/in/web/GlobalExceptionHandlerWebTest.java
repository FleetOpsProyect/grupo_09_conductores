package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
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
 * Web slice tests for unknown routes, unsupported methods, unexpected errors (TEC_04-2) and
 * persistence unavailable (TEC_04-4).
 */
@WebMvcTest(DriverController.class)
@Import(JsonFieldNameResolver.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerWebTest {

    private static final List<String> COMMON_KEYS = List.of("code", "message", "timestamp", "path");

    private static final UUID DRIVER_ID = UUID.fromString("7b0e4a52-2f1c-4c55-9d3e-1a2b3c4d5e6f");

    private static final String DRIVER_JSON = """
            {"full_name": "Carlos Pérez", "document_number": "1144056789",
             "email": "carlos@correo.com", "phone": "3001234567"}
            """;

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
    void registerReturns503WhenPersistenceIsUnavailable(CapturedOutput output) throws Exception {
        when(registerDriver.register(any())).thenThrow(unavailable());

        MockHttpServletResponse response = perform(post("/drivers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(DRIVER_JSON));

        assertServiceUnavailable(response, "/drivers", output, "POST");
    }

    @Test
    void getReturns503AndNot404WhenPersistenceIsUnavailable(CapturedOutput output) throws Exception {
        when(getDriver.get(DRIVER_ID)).thenThrow(unavailable());

        MockHttpServletResponse response = perform(get("/drivers/" + DRIVER_ID));

        assertServiceUnavailable(response, "/drivers/" + DRIVER_ID, output, "GET");
    }

    @Test
    void listReturns503WhenPersistenceIsUnavailable(CapturedOutput output) throws Exception {
        when(listDriver.list(any())).thenThrow(unavailable());

        MockHttpServletResponse response = perform(get("/drivers"));

        assertServiceUnavailable(response, "/drivers", output, "GET");
    }

    @Test
    void updateReturns503AndNot404WhenPersistenceIsUnavailable(CapturedOutput output) throws Exception {
        when(updateDriver.update(any(), any())).thenThrow(unavailable());

        MockHttpServletResponse response = perform(put("/drivers/" + DRIVER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(DRIVER_JSON));

        assertServiceUnavailable(response, "/drivers/" + DRIVER_ID, output, "PUT");
    }

    @Test
    void unsupportedMediaTypeKeepsSpringStatus() throws Exception {
        MockHttpServletResponse response = perform(post("/drivers")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"));

        assertThat(response.getStatus()).isEqualTo(415);
    }

    private static PersistenceUnavailableException unavailable() {
        return new PersistenceUnavailableException("secret detail", new TimeoutException("sdk timeout"));
    }

    private void assertServiceUnavailable(MockHttpServletResponse response, String path, CapturedOutput output,
            String method) throws Exception {
        assertThat(response.getStatus()).isEqualTo(503);
        String raw = response.getContentAsString();
        JsonNode body = jsonMapper.readTree(raw);
        assertCommonFormat(body);
        assertThat(body.get("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(body.get("message").asString())
                .isEqualTo("Servicio temporalmente no disponible, intente nuevamente");
        assertThat(body.get("path").asString()).isEqualTo(path);
        assertThat(OffsetDateTime.parse(body.get("timestamp").asString()).getOffset().getId())
                .isEqualTo("-05:00");
        assertThat(raw).doesNotContain("secret detail", "sdk timeout", "Exception", "at co.edu");
        String log = output.getOut();
        String line = "Persistence unavailable on " + method + " " + path;
        assertThat(log).contains("ERROR").contains(line);
        assertThat(log.split(Pattern.quote(line), -1)).hasSize(2);
        assertThat(log).doesNotContain("Unexpected error on");
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
