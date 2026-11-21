package com.ticketya.catalog.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventNotificationConfig {

    @Bean
    public EventNotificationPublisher eventNotificationPublisher(
            @Value("${ticketya.notifications.url:}") String url, ObjectMapper mapper) {
        return new EventNotificationPublisher(url, mapper);
    }
}
