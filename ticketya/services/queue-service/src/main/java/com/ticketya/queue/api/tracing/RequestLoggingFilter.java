package com.ticketya.queue.api.tracing;

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
 * Un log por request con método, path, cliente, status y duración. Sólo se
 * registran encabezados y metadatos: el token de admisión viaja en el cuerpo
 * y nunca se loguea.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} user={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    valueOrDash(request.getHeader("X-User-Id")),
                    response.getStatus(),
                    durationMs);
        }
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
