package com.ticketya.catalog.application.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class CatalogClockConfig {
    @Bean Clock catalogClock() { return Clock.systemUTC(); }
}
