package com.ticketya.catalog.api;

import com.ticketya.catalog.api.error.GlobalExceptionHandler;
import com.ticketya.catalog.api.tracing.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TestProbeController.class)
@Import({CorrelationIdFilter.class, GlobalExceptionHandler.class})
public class CatalogHttpInfrastructureIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void propagatesSuppliedCorrelationIdOnSuccessfulResponse() throws Exception {
        mockMvc.perform(get("/test/ok").header(CorrelationIdFilter.HEADER_NAME, "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "trace-123"));
    }

    @Test
    void generatesCorrelationIdWhenRequestDoesNotSupplyOne() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")));
    }

    @Test
    void returnsProblemJsonWithRequiredFieldsAndCorrelationId() throws Exception {
        mockMvc.perform(get("/test/problem")
                        .header(CorrelationIdFilter.HEADER_NAME, "problem-trace"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "problem-trace"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Test conflict"))
                .andExpect(jsonPath("$.detail").value("The test resource is not ready"))
                .andExpect(jsonPath("$.instance").value("/test/problem"))
                .andExpect(jsonPath("$.correlationId").value("problem-trace"));
    }
}
