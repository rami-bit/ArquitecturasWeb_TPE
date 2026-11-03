package com.ticketya.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogServiceApplicationTest {
    @Test
    void applicationEntryPointExists() {
        assertThat(CatalogServiceApplication.class).isNotNull();
    }

    @Test
    void schemaBootstrapIsPackagedAndIdempotent() throws Exception {
        var schema = new ClassPathResource("schema.sql");

        assertThat(schema.exists()).isTrue();
        assertThat(schema.getContentAsString(StandardCharsets.UTF_8))
                .contains("CREATE SCHEMA IF NOT EXISTS public");
    }
}
