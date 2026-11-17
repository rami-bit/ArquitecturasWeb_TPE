package com.ticketya.queue;

import com.ticketya.queue.config.QueueProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(QueueProperties.class)
public class QueueServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueueServiceApplication.class, args);
    }
}
