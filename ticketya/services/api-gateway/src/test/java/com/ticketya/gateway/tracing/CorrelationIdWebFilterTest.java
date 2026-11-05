package com.ticketya.gateway.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {
    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void preservesSuppliedCorrelationId() {
        MockServerWebExchange exchange = exchangeWithHeader("trace-123");

        ServerWebExchange filtered = applyFilter(exchange);

        assertThat(filtered.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME))
                .isEqualTo("trace-123");
        assertThat(filtered.getAttributes().get(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE))
                .isEqualTo("trace-123");
        assertResponseHeader(exchange, "trace-123");
    }

    @Test
    void trimsSuppliedCorrelationId() {
        MockServerWebExchange exchange = exchangeWithHeader("  trace-123  ");

        ServerWebExchange filtered = applyFilter(exchange);

        assertThat(filtered.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME))
                .isEqualTo("trace-123");
        assertResponseHeader(exchange, "trace-123");
    }

    @Test
    void generatesUuidV4WhenCorrelationIdIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events").build());

        ServerWebExchange filtered = applyFilter(exchange);

        String correlationId = filtered.getRequest().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER_NAME);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId).version()).isEqualTo(4);
        assertResponseHeader(exchange, correlationId);
    }

    @Test
    void generatesUuidV4WhenCorrelationIdIsBlank() {
        MockServerWebExchange exchange = exchangeWithHeader("   ");

        ServerWebExchange filtered = applyFilter(exchange);

        String correlationId = filtered.getRequest().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER_NAME);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId).version()).isEqualTo(4);
        assertResponseHeader(exchange, correlationId);
    }

    @Test
    void preservesOtherRequestHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/management/events")
                        .header(CorrelationIdWebFilter.HEADER_NAME, "trace-123")
                        .header("Accept", "application/json")
                        .header("X-User-Id", "organizer-1")
                        .header("X-User-Role", "ORGANIZER")
                        .build());

        ServerWebExchange filtered = applyFilter(exchange);

        assertThat(filtered.getRequest().getHeaders().getFirst("Accept"))
                .isEqualTo("application/json");
        assertThat(filtered.getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo("organizer-1");
        assertThat(filtered.getRequest().getHeaders().getFirst("X-User-Role"))
                .isEqualTo("ORGANIZER");
    }

    private MockServerWebExchange exchangeWithHeader(String value) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events")
                        .header(CorrelationIdWebFilter.HEADER_NAME, value)
                        .build());
    }

    private ServerWebExchange applyFilter(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, filtered -> {
            captured.set(filtered);
            return Mono.empty();
        }).block();
        return captured.get();
    }

    private void assertResponseHeader(MockServerWebExchange exchange, String expected) {
        exchange.getResponse().setComplete().block();
        assertThat(exchange.getResponse().getHeaders().get(CorrelationIdWebFilter.HEADER_NAME))
                .containsExactly(expected);
    }
}
