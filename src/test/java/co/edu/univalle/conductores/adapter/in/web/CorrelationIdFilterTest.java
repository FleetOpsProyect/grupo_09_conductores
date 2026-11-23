package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.application.logging.LogContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final String ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void reusesValidHeaderAndExposesItInContextAttributeAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/drivers");
        request.addHeader(LogContext.CORRELATION_ID_HEADER, ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> seen = new HashMap<>();

        filter.doFilter(request, response, (req, res) -> seen.putAll(MDC.getCopyOfContextMap()));

        assertThat(seen).containsEntry(LogContext.CORRELATION_ID, ID)
                .containsEntry(LogContext.HTTP_METHOD, "POST")
                .containsEntry(LogContext.HTTP_PATH, "/drivers");
        assertThat(request.getAttribute(CorrelationIdFilter.ATTRIBUTE)).isEqualTo(UUID.fromString(ID));
        assertThat(response.getHeader(LogContext.CORRELATION_ID_HEADER)).isEqualTo(ID);
    }

    @Test
    void generatesIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/drivers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        UUID generated = (UUID) request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        assertThat(generated).isNotNull();
        assertThat(response.getHeader(LogContext.CORRELATION_ID_HEADER)).isEqualTo(generated.toString());
    }

    @Test
    void generatesIdWhenHeaderIsNotAUuid() {
        assertThat(CorrelationIdFilter.resolve("abc")).isNotNull();
        assertThat(CorrelationIdFilter.resolve("")).isNotNull();
        assertThat(CorrelationIdFilter.resolve("  " + ID + " ")).isEqualTo(UUID.fromString(ID));
    }

    @Test
    void clearsLogContextAfterTheRequestEvenOnError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/drivers");

        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // the filter must not swallow the error
        }

        assertThat(MDC.get(LogContext.CORRELATION_ID)).isNull();
        assertThat(MDC.get(LogContext.HTTP_METHOD)).isNull();
        assertThat(MDC.get(LogContext.HTTP_PATH)).isNull();
    }
}
