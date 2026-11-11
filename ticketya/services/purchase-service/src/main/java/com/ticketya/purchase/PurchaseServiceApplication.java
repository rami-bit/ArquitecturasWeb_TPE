package com.ticketya.purchase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class PurchaseServiceApplication {
    @Bean Clock clock() { return Clock.systemUTC(); }
    public static void main(String[] args) { SpringApplication.run(PurchaseServiceApplication.class, args); }
}
