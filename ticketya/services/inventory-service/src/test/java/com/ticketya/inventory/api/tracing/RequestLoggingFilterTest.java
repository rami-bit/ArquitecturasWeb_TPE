package com.ticketya.inventory.api.tracing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    @Test
    void logsEndpointUserStatusDurationAndCorrelationId(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/inventory/probe");
        request.setQueryString("operation=PURCHASE_ORDER");
        request.addHeader("X-User-Id", "7f9c2f43-8a8f-4c1e-9c43-6f0f1c2b9a10");
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-Correlation-Id", "log-test-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse res) {
                res.setStatus(418);
            }
        }, new RequestLoggingFilter());

        new CorrelationIdFilter().doFilter(request, response, chain);

        assertThat(output.getOut())
                .contains("http method=GET path=/api/inventory/probe?operation=PURCHASE_ORDER status=418")
                .contains("userId=7f9c2f43-8a8f-4c1e-9c43-6f0f1c2b9a10")
                .contains("role=ADMIN")
                .contains("correlationId=log-test-1")
                .containsPattern("durationMs=\\d+");
    }

    @Test
    void anonymousRequestsAreLoggedWithDashes() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/inventory/probe");
        String line = RequestLoggingFilter.format(request, 202, 5);
        assertThat(line).contains("userId=- role=-").contains("status=202").contains("durationMs=5");
    }
}
