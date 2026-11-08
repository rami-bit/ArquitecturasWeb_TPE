package com.ticketya.inventory.contract;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque y salud del servicio: el contexto completo levanta contra PostgreSQL real y el
 * endpoint de actuator responde, con el correlation ID propagado por el filtro.
 */
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
class InventoryHealthContractTest extends InventoryContractTestBase {

    @Test
    void actuatorHealthIsUpAndCarriesTheCorrelationId() {
        var response = get("/actuator/health");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(response).path("status").asText()).isEqualTo("UP");
        assertThat(response.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();
    }
}
