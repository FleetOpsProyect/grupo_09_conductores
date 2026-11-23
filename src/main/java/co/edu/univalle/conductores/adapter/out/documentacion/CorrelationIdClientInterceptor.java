package co.edu.univalle.conductores.adapter.out.documentacion;

import co.edu.univalle.conductores.application.logging.LogContext;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Envía el correlation_id de la operación en curso en el encabezado {@code X-Correlation-Id} de las
 * llamadas salientes (TEC_03).
 *
 * <p>El cliente de Documentación (HU-07) debe registrarlo con
 * {@code RestClient.builder().requestInterceptor(interceptor)}. Si la petición ya trae el encabezado, o no
 * hay correlation_id en el contexto de log, no lo toca.
 */
@Component
public class CorrelationIdClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsHeader(LogContext.CORRELATION_ID_HEADER)) {
            LogContext.currentCorrelationId()
                    .ifPresent(id -> request.getHeaders().set(LogContext.CORRELATION_ID_HEADER, id));
        }
        return execution.execute(request, body);
    }
}
