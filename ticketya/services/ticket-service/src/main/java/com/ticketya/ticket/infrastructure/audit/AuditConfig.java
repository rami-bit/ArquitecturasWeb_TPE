package com.ticketya.ticket.infrastructure.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditConfig {

    @Bean
    public AuditPublisher auditPublisher(
            @Value("${ticketya.audit.url:}") String auditUrl,
            @Value("${spring.application.name}") String serviceName,
            ObjectMapper mapper) {
        return new AuditPublisher(auditUrl, serviceName, mapper);
    }
}
