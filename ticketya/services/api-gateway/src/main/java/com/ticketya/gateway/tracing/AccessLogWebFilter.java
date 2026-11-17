package com.ticketya.gateway.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * E2-02: registro de acceso del punto de entrada único. Una línea por
 * solicitud con método, endpoint, usuario (sub del JWT), rol, código de
 * respuesta, duración y {@code X-Correlation-Id}. Se ejecuta antes de la
 * seguridad, por lo que también registra los 401 y 403 que no llegan a los
 * servicios. Los healthchecks ({@code /actuator/**}) van en DEBUG.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogWebFilter implements WebFilter {
    public static final String LOGGER_NAME = "ticketya.http";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String line = format(exchange, signal, durationMs);
            if (exchange.getRequest().getPath().value().startsWith("/actuator")) {
                log.debug(line);
            } else {
                log.info(line);
            }
        });
    }

    static String format(ServerWebExchange exchange, SignalType signal, long durationMs) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        String statusText = status != null ? String.valueOf(status.value())
                : signal == SignalType.CANCEL ? "499" : signal == SignalType.ON_ERROR ? "500" : "200";
        String query = exchange.getRequest().getURI().getRawQuery();
        String path = exchange.getRequest().getPath().value() + (query == null ? "" : "?" + query);
        return "http method=" + exchange.getRequest().getMethod().name()
                + " path=" + path
                + " status=" + statusText
                + " durationMs=" + durationMs
                + " userId=" + dash(exchange.getAttribute(AuthenticatedIdentityCaptureFilter.USER_ID_ATTRIBUTE))
                + " role=" + dash(exchange.getAttribute(AuthenticatedIdentityCaptureFilter.USER_ROLE_ATTRIBUTE))
                + " correlationId=" + dash(exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE));
    }

    private static String dash(Object value) {
        return value == null || value.toString().isBlank() ? "-" : value.toString();
    }
}
