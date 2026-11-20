package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.univalle.conductores.application.logging.LogContext;
import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.ReleaseMedicalBlock;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.domain.Driver;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Logs en JSON con nivel, ruta HTTP y correlation_id, sin datos de contacto completos (TEC_03).
 */
@WebMvcTest(DriverController.class)
@Import(JsonFieldNameResolver.class)
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingWebTest {

    private static final String CORRELATION_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private static final JsonMapper LOG_MAPPER = new JsonMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterDriver registerDriver;

    @MockitoBean
    private GetDriver getDriver;

    @MockitoBean
    private ListDriver listDriver;

    @MockitoBean
    private UpdateDriver updateDriver;

    @MockitoBean
    private ReleaseMedicalBlock releaseMedicalBlock;

    @Test
    void errorLogIsJsonWithLevelRouteAndCorrelationIdAndNoContactData(CapturedOutput output) throws Exception {
        when(listDriver.list(any())).thenThrow(
                new RuntimeException("fallo con carlos@correo.com y 3001234567"));

        MockHttpServletResponse response = mockMvc.perform(get("/drivers")
                .header(LogContext.CORRELATION_ID_HEADER, CORRELATION_ID)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader(LogContext.CORRELATION_ID_HEADER)).isEqualTo(CORRELATION_ID);
        JsonNode log = jsonLine(output, "Unexpected error on GET /drivers");
        // ECS schema (TEC_03, V6.5): level and logger under log, the exception under error.
        assertThat(log.at("/ecs/version").isMissingNode()).isFalse();
        assertThat(log.at("/log/level").asString()).isEqualTo("ERROR");
        assertThat(log.at("/service/name").asString()).isEqualTo("ms-conductores");
        assertThat(log.get("correlation_id").asString()).isEqualTo(CORRELATION_ID);
        assertThat(log.get("http_method").asString()).isEqualTo("GET");
        assertThat(log.get("http_path").asString()).isEqualTo("/drivers");
        assertThat(log.has("@timestamp")).isTrue();
        assertThat(log.at("/error/type").asString()).isEqualTo("java.lang.RuntimeException");
        assertThat(log.at("/error/stack_trace").asString()).contains("c***@correo.com", "******4567");
        assertThat(output.getOut()).doesNotContain("carlos@correo.com", "3001234567");
    }

    @Test
    void generatedCorrelationIdIsReturnedAndPassedToTheUseCase() throws Exception {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null,
                OffsetDateTime.now());
        when(registerDriver.register(any())).thenReturn(driver);

        MockHttpServletResponse response = mockMvc.perform(post("/drivers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"full_name": "Carlos Pérez", "document_number": "1144056789",
                         "email": "carlos@correo.com"}
                        """)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(201);
        String header = response.getHeader(LogContext.CORRELATION_ID_HEADER);
        assertThat(response.getHeaders(LogContext.CORRELATION_ID_HEADER)).hasSize(1);
        ArgumentCaptor<RegisterDriver.Command> command = ArgumentCaptor.forClass(RegisterDriver.Command.class);
        Mockito.verify(registerDriver).register(command.capture());
        assertThat(command.getValue().correlationId()).hasToString(header);
    }

    private static JsonNode jsonLine(CapturedOutput output, String message) {
        return output.getOut().lines()
                .filter(line -> line.startsWith("{"))
                .map(LOG_MAPPER::readTree)
                .filter(node -> node.path("message").asString().contains(message))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay una línea JSON con el mensaje: " + message));
    }
}
