package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.univalle.conductores.application.logging.LogContext;
import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.ListValidDrivers;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.ReleaseMedicalBlock;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import co.edu.univalle.conductores.domain.StatusNotAllowedException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Pruebas de integración HTTP del endpoint POST /drivers/{id}/medical-release
 * (HU10-3).
 */
@WebMvcTest(DriverController.class)
@Import({ JsonFieldNameResolver.class, CorrelationIdFilter.class })
@ExtendWith(OutputCaptureExtension.class)
class MedicalReleaseWebTest {

    private static final UUID DRIVER_ID = UUID.fromString("7b0e4a52-2f1c-4c55-9d3e-1a2b3c4d5e6f");
    private static final String CORRELATION_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final JsonMapper MAPPER = new JsonMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterDriver registerDriver;

    @MockitoBean
    private GetDriver getDriver;

    @MockitoBean
    private ListDriver listDriver;

    @MockitoBean
    private ListValidDrivers listValidDrivers;

    @MockitoBean
    private UpdateDriver updateDriver;

    @MockitoBean
    private ReleaseMedicalBlock releaseMedicalBlock;

    @Test
    void medicalReleaseWithoutBodyReturns200() throws Exception {
        Driver driver = activeDriver();
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any())).thenReturn(driver);

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.path("id").asString()).isEqualTo(DRIVER_ID.toString());
        assertThat(body.has("notes")).isFalse();
        assertThat(body.has("medical_release_notes")).isFalse();
        assertThat(body.has("medical_released_at")).isFalse();

        ArgumentCaptor<ReleaseMedicalBlock.Command> captor = ArgumentCaptor
                .forClass(ReleaseMedicalBlock.Command.class);
        verify(releaseMedicalBlock).release(eq(DRIVER_ID), captor.capture());
        assertThat(captor.getValue().notes()).isNull();
    }

    @Test
    void medicalReleaseWithEmptyJsonReturns200() throws Exception {
        Driver driver = activeDriver();
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any())).thenReturn(driver);

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("status").asString()).isEqualTo("ACTIVE");

        ArgumentCaptor<ReleaseMedicalBlock.Command> captor = ArgumentCaptor
                .forClass(ReleaseMedicalBlock.Command.class);
        verify(releaseMedicalBlock).release(eq(DRIVER_ID), captor.capture());
        assertThat(captor.getValue().notes()).isNull();
    }

    @Test
    void medicalReleaseWithValidNotesReturns200AndDoesNotExposeNotes() throws Exception {
        Driver driver = activeDriver();
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any())).thenReturn(driver);

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Alta médica otorgada sin secuelas\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.has("notes")).isFalse();
        assertThat(response.getContentAsString()).doesNotContain("Alta médica otorgada sin secuelas");

        ArgumentCaptor<ReleaseMedicalBlock.Command> captor = ArgumentCaptor
                .forClass(ReleaseMedicalBlock.Command.class);
        verify(releaseMedicalBlock).release(eq(DRIVER_ID), captor.capture());
        assertThat(captor.getValue().notes()).isEqualTo("Alta médica otorgada sin secuelas");
    }

    @Test
    void medicalReleaseWith500CharsNotesReturns200() throws Exception {
        Driver driver = activeDriver();
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any())).thenReturn(driver);

        String notes500 = "x".repeat(500);
        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"" + notes500 + "\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        ArgumentCaptor<ReleaseMedicalBlock.Command> captor = ArgumentCaptor
                .forClass(ReleaseMedicalBlock.Command.class);
        verify(releaseMedicalBlock).release(eq(DRIVER_ID), captor.capture());
        assertThat(captor.getValue().notes()).isEqualTo(notes500);
    }

    @Test
    void medicalReleaseWhenDocumentBlockPresentReturns200WithInactiveExpiredDocuments() throws Exception {
        Driver driver = Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com", null,
                true, false, OffsetDateTime.now(), OffsetDateTime.now());
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any())).thenReturn(driver);

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("status").asString()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS.name());
    }

    @Test
    void medicalReleaseWith501CharsNotesReturns400InvalidLength() throws Exception {
        String notes501 = "x".repeat(501);
        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"" + notes501 + "\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("INVALID_DATA");
        JsonNode errors = body.path("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path("field").asString()).isEqualTo("notes");
        assertThat(errors.get(0).path("code").asString()).isEqualTo("INVALID_LENGTH");
    }

    @Test
    void medicalReleaseWithNonExistentIdReturns404DriverNotFound() throws Exception {
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any()))
                .thenThrow(new DriverNotFoundException(DRIVER_ID));

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("DRIVER_NOT_FOUND");
        assertThat(body.path("message").asString())
                .isEqualTo("No existe un conductor con id '" + DRIVER_ID + "'");
    }

    @Test
    void medicalReleaseWithInvalidIdFormatReturns404DriverNotFound() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", "formato-invalido"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("DRIVER_NOT_FOUND");
        assertThat(body.path("message").asString())
                .isEqualTo("No existe un conductor con id 'formato-invalido'");
    }

    @Test
    void medicalReleaseWithoutMedicalBlockReturns409StatusNotAllowedWithExactMessage() throws Exception {
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any()))
                .thenThrow(new StatusNotAllowedException(
                        "El conductor no está inhabilitado por incidente"));

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(409);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("STATUS_NOT_ALLOWED");
        assertThat(body.path("message").asString())
                .isEqualTo("El conductor no está inhabilitado por incidente");
    }

    @Test
    void medicalReleaseWhenFirestoreDownReturns503ServiceUnavailable() throws Exception {
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any()))
                .thenThrow(new PersistenceUnavailableException("Firestore timeout", null));

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(503);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(body.path("message").asString())
                .isEqualTo("Servicio temporalmente no disponible, intente nuevamente");
    }

    @Test
    void medicalReleaseEmitsInfoLogWithDriverIdAndCorrelationIdWhenHeaderPresent(
            CapturedOutput output) throws Exception {
        Driver driver = activeDriver();
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any())).thenReturn(driver);

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID)
                        .header(LogContext.CORRELATION_ID_HEADER, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Nota confidencial\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(LogContext.CORRELATION_ID_HEADER)).isEqualTo(CORRELATION_ID);

        JsonNode logLine = jsonLine(output, "Alta médica registrada para el conductor");
        assertThat(logLine.at("/log/level").asString()).isEqualTo("INFO");
        assertThat(logLine.path("message").asString())
                .contains(DRIVER_ID.toString());
        assertThat(logLine.path("correlation_id").asString()).isEqualTo(CORRELATION_ID);
        assertThat(logLine.path("http_path").asString())
                .isEqualTo("/drivers/" + DRIVER_ID + "/medical-release");
        assertThat(logLine.path("http_method").asString()).isEqualTo("POST");
        assertThat(output.getOut()).doesNotContain("Nota confidencial");
    }

    @Test
    void medicalReleaseEmitsInfoLogWithDriverIdAndGeneratedCorrelationIdWhenHeaderAbsent(
            CapturedOutput output) throws Exception {
        Driver driver = activeDriver();
        when(releaseMedicalBlock.release(eq(DRIVER_ID), any())).thenReturn(driver);

        MockHttpServletResponse response = mockMvc.perform(
                post("/drivers/{id}/medical-release", DRIVER_ID))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        String generatedCorrelationId = response.getHeader(LogContext.CORRELATION_ID_HEADER);
        assertThat(generatedCorrelationId).isNotNull();

        JsonNode logLine = jsonLine(output, "Alta médica registrada para el conductor");
        assertThat(logLine.at("/log/level").asString()).isEqualTo("INFO");
        assertThat(logLine.path("message").asString()).contains(DRIVER_ID.toString());
        assertThat(logLine.path("correlation_id").asString()).isEqualTo(generatedCorrelationId);
    }

    private static Driver activeDriver() {
        OffsetDateTime now = OffsetDateTime.now();
        return Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com", null,
                false, false, now, now);
    }

    private static JsonNode jsonLine(CapturedOutput output, String message) {
        return output.getOut().lines()
                .filter(line -> line.startsWith("{"))
                .map(MAPPER::readTree)
                .filter(node -> node.path("message").asString().contains(message))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No hay una línea JSON con el mensaje: " + message));
    }
}
