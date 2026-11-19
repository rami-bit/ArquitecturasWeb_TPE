package com.ticketya.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.gateway.tracing.CorrelationIdWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 403 en {@code application/problem+json} cuando el JWT es válido pero su rol
 * no está autorizado para la ruta (E2-02/E2-03/E2-04). El upstream no se invoca.
 */
public class ProblemJsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE);
        if (correlationId != null) {
            response.getHeaders().set(CorrelationIdWebFilter.HEADER_NAME, correlationId);
        }
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Forbidden");
        problem.put("status", 403);
        problem.put("detail", "The authenticated role is not allowed to access this resource");
        problem.put("instance", exchange.getRequest().getPath().value());
        problem.put("correlationId", correlationId != null ? correlationId : "");
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException exception) {
            bytes = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
