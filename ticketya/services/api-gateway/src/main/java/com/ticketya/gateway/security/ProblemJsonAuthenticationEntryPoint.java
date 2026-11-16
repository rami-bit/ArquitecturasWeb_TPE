package com.ticketya.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.gateway.tracing.CorrelationIdWebFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProblemJsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String correlationId = exchange.getAttribute(
                CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE);
        if (correlationId != null) {
            response.getHeaders().set(CorrelationIdWebFilter.HEADER_NAME, correlationId);
        }

        String instance = exchange.getRequest().getPath().value();
        String detail = exception.getMessage() != null
                ? exception.getMessage()
                : "Authentication required";

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Unauthorized");
        problem.put("status", 401);
        problem.put("detail", detail);
        problem.put("instance", instance);
        problem.put("correlationId", correlationId != null ? correlationId : "");

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException jsonException) {
            bytes = fallbackProblem(instance, correlationId);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] fallbackProblem(String instance, String correlationId) {
        String body = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"Authentication required\",\"instance\":\""
                + escape(instance) + "\",\"correlationId\":\"" + escape(correlationId) + "\"}";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
