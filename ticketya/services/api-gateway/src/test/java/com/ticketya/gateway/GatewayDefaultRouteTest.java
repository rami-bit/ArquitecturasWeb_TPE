package com.ticketya.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayDefaultRouteTest {
    @Autowired
    private RouteLocator routeLocator;

    @Test
    void fallsBackToLocalCatalogWhenCatalogServiceUrlIsNotDefined() {
        assertThat(routeLocator.getRoutes().collectList().block())
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().toString())
                        .isEqualTo("http://localhost:8081"));
    }
}
