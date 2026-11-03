package com.ticketya.catalog.api;

import com.ticketya.catalog.api.error.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestProbeController {
    @GetMapping("/test/ok")
    String ok() {
        return "ok";
    }

    @GetMapping("/test/problem")
    String problem() {
        throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "Test conflict",
                "The test resource is not ready");
    }
}
