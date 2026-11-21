package com.ticketya.notification.api.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registro de acceso HTTP (E2-02): una línea por solicitud con método,
 * endpoint, usuario, rol, código de respuesta, duración y correlation id.
 * Se ejecuta inmediatamente después de {@link CorrelationIdFilter}, por lo que
 * el MDC ya contiene el identificador de correlación.
 *
 * <p>Las comprobaciones de salud ({@code /actuator/**}) se registran en DEBUG
 * para no inundar el log con los healthchecks periódicos de Compose.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String LOGGER_NAME = "ticketya.http";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        int status = 500;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String line = format(request, status, durationMs);
            if (request.getRequestURI().startsWith("/actuator")) {
                log.debug(line);
            } else {
                log.info(line);
            }
        }
    }

    static String format(HttpServletRequest request, int status, long durationMs) {
        String query = request.getQueryString();
        String path = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
        return "http method=" + request.getMethod()
                + " path=" + path
                + " status=" + status
                + " durationMs=" + durationMs
                + " userId=" + valueOrDash(request.getHeader("X-User-Id"))
                + " role=" + valueOrDash(request.getHeader("X-User-Role"))
                + " correlationId=" + valueOrDash(CorrelationIdFilter.get(request));
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
