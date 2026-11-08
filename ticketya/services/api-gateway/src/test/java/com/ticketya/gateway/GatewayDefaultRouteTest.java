package com.ticketya.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayDefaultRouteTest {
    @Autowired
    private RouteLocator routeLocator;

    @Test
    void fallsBackToLocalCatalogAndInventoryWhenServiceUrlsAreNotDefined() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        assertThat(routes)
                .filteredOn(route -> "catalog-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().toString())
                        .isEqualTo("http://localhost:8081"));

        assertThat(routes)
                .filteredOn(route -> "inventory-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().toString())
                        .isEqualTo("http://localhost:8082"));
    }
}
