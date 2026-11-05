package com.ticketya.gateway.tracing;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {
    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTRIBUTE =
            CorrelationIdWebFilter.class.getName() + ".correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = resolveCorrelationId(
                exchange.getRequest().getHeaders().getFirst(HEADER_NAME));

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER_NAME, correlationId)))
                .build();

        mutatedExchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
            return Mono.empty();
        });

        return chain.filter(mutatedExchange);
    }

    private static String resolveCorrelationId(String supplied) {
        return supplied == null || supplied.isBlank()
                ? UUID.randomUUID().toString()
                : supplied.trim();
    }
}
