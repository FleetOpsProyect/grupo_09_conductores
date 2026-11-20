package co.edu.univalle.conductores.adapter.in.web;

import co.edu.univalle.conductores.application.logging.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resuelve el correlation_id de cada petición HTTP (TEC_03).
 *
 * <p>Lo toma del encabezado {@code X-Correlation-Id} si es un UUID canónico y lo genera si no llega o no
 * es válido. Lo deja en el contexto de log junto con el método y la ruta, para que todos los logs de la
 * petición lo incluyan, y lo devuelve en el encabezado de la respuesta, también en los errores.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Atributo de la petición con el correlation_id resuelto, como {@link UUID}. */
    public static final String ATTRIBUTE = "co.edu.univalle.conductores.correlationId";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID correlationId = resolve(request.getHeader(LogContext.CORRELATION_ID_HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(LogContext.CORRELATION_ID_HEADER, correlationId.toString());
        MDC.put(LogContext.CORRELATION_ID, correlationId.toString());
        MDC.put(LogContext.HTTP_METHOD, request.getMethod());
        MDC.put(LogContext.HTTP_PATH, request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogContext.CORRELATION_ID);
            MDC.remove(LogContext.HTTP_METHOD);
            MDC.remove(LogContext.HTTP_PATH);
        }
    }

    /**
     * Usa el valor del encabezado si es un UUID canónico; en otro caso genera uno nuevo.
     *
     * @param header valor del encabezado X-Correlation-Id, puede ser null
     * @return correlation_id de la petición
     */
    static UUID resolve(String header) {
        if (header == null || !UUID_PATTERN.matcher(header.trim()).matches()) {
            return UUID.randomUUID();
        }
        return UUID.fromString(header.trim());
    }
}
