package co.edu.univalle.conductores.adapter.out.documentacion;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.application.logging.LogContext;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class CorrelationIdClientInterceptorTest {

    private static final String ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private final CorrelationIdClientInterceptor interceptor = new CorrelationIdClientInterceptor();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void sendsCurrentCorrelationId() throws Exception {
        MDC.put(LogContext.CORRELATION_ID, ID);

        HttpRequest sent = intercept(newRequest());

        assertThat(sent.getHeaders().getFirst(LogContext.CORRELATION_ID_HEADER)).isEqualTo(ID);
    }

    @Test
    void keepsHeaderAlreadySetByTheCaller() throws Exception {
        MDC.put(LogContext.CORRELATION_ID, ID);
        MockClientHttpRequest request = newRequest();
        request.getHeaders().set(LogContext.CORRELATION_ID_HEADER, "propio");

        HttpRequest sent = intercept(request);

        assertThat(sent.getHeaders().get(LogContext.CORRELATION_ID_HEADER)).containsExactly("propio");
    }

    @Test
    void sendsNothingWithoutCorrelationIdInContext() throws Exception {
        HttpRequest sent = intercept(newRequest());

        assertThat(sent.getHeaders().containsHeader(LogContext.CORRELATION_ID_HEADER)).isFalse();
    }

    private HttpRequest intercept(MockClientHttpRequest request) throws Exception {
        HttpRequest[] executed = new HttpRequest[1];
        interceptor.intercept(request, new byte[0], (req, body) -> {
            executed[0] = req;
            return new MockClientHttpResponse();
        });
        return executed[0];
    }

    private static MockClientHttpRequest newRequest() {
        return new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://documentacion/documents/validity-check"));
    }
}
