package com.ticketya.purchase.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void preservesExpectedHttpStatusesFromRefundAndCancellationControllers() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new StatusController())
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();

        for (HttpStatus expected : new HttpStatus[]{HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT}) {
            mvc.perform(get("/test-status/" + expected.value()))
                    .andExpect(status().is(expected.value()))
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(expected.value()))
                    .andExpect(jsonPath("$.detail").value("Expected failure"));
        }
    }

    @RestController
    public static class StatusController {
        @GetMapping("/test-status/{code}")
        public void fail(@PathVariable("code") int code) {
            throw new ResponseStatusException(HttpStatus.valueOf(code), "Expected failure");
        }
    }
}
