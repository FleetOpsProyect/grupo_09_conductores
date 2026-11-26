package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.univalle.conductores.application.port.in.GetDriver;
import co.edu.univalle.conductores.application.port.in.ListDriver;
import co.edu.univalle.conductores.application.port.in.RegisterDriver;
import co.edu.univalle.conductores.application.port.in.ReleaseMedicalBlock;
import co.edu.univalle.conductores.application.port.in.UpdateDriver;
import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas de controlador para la validación de {@code valid_until} en GET /drivers (HU07-1).
 */
@WebMvcTest(DriverController.class)
@Import({ JsonFieldNameResolver.class, CorrelationIdFilter.class })
class ValidUntilValidationWebTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    // 2026-11-25T23:30:00-05:00 -> en UTC ya es 2026-11-26, pero en America/Bogota sigue siendo 2026-11-25.
    private static final Instant NOW_BOGOTA = Instant.parse("2026-11-26T04:30:00Z");
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
    private UpdateDriver updateDriver;

    @MockitoBean
    private ReleaseMedicalBlock releaseMedicalBlock;

    @MockitoBean
    private DocumentValidityPort documentValidityPort;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW_BOGOTA);
        when(clock.getZone()).thenReturn(BOGOTA);
        when(clock.withZone(BOGOTA)).thenReturn(Clock.fixed(NOW_BOGOTA, BOGOTA));
    }

    @Test
    void invalidDateFormatReturns400AndNeverCallsPort() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/drivers").param("valid_until", "05-11-2026"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("INVALID_DATA");
        assertThat(body.path("errors")).hasSize(1);
        assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("valid_until");
        assertThat(body.path("errors").get(0).path("code").asString()).isEqualTo("INVALID_FORMAT");
        verifyNoInteractions(documentValidityPort, listDriver);
    }

    @Test
    void impossibleCalendarDateReturns400InvalidFormatAndNeverCallsPort() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/drivers").param("valid_until", "2026-02-30"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("INVALID_DATA");
        assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("valid_until");
        assertThat(body.path("errors").get(0).path("code").asString()).isEqualTo("INVALID_FORMAT");
        verifyNoInteractions(documentValidityPort, listDriver);
    }

    @Test
    void pastDateInBogotaTimezoneReturns400ValueNotAllowedAndNeverCallsPort() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/drivers").param("valid_until", "2026-11-24"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("INVALID_DATA");
        assertThat(body.path("errors")).hasSize(1);
        assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("valid_until");
        assertThat(body.path("errors").get(0).path("code").asString()).isEqualTo("VALUE_NOT_ALLOWED");
        verifyNoInteractions(documentValidityPort, listDriver);
    }

    @Test
    void todayInBogotaTimezoneReturns200() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/drivers").param("status", "ACTIVE").param("valid_until", "2026-11-25"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void validUntilIgnoresPageAndSizeAndReturns200WithSize999() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/drivers")
                        .param("status", "ACTIVE")
                        .param("valid_until", "2026-11-25")
                        .param("page", "-5")
                        .param("size", "999"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void invalidStatusWithValidUntilReturns400ValueNotAllowedAndNeverCallsPort() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/drivers")
                        .param("status", "UNKNOWN")
                        .param("valid_until", "2026-11-25"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("INVALID_DATA");
        assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("status");
        assertThat(body.path("errors").get(0).path("code").asString()).isEqualTo("VALUE_NOT_ALLOWED");
        verifyNoInteractions(documentValidityPort, listDriver);
    }

    @Test
    void withoutValidUntilSize999StillReturns400() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/drivers").param("size", "999"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("INVALID_DATA");
        assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("size");
        assertThat(body.path("errors").get(0).path("code").asString()).isEqualTo("VALUE_NOT_ALLOWED");
        verifyNoInteractions(documentValidityPort, listDriver);
    }
}
