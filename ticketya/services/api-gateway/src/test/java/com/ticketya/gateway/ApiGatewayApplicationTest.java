package com.ticketya.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiGatewayApplicationTest {
    @Test
    void applicationEntryPointExists() {
        assertThat(ApiGatewayApplication.class).isNotNull();
    }
}
