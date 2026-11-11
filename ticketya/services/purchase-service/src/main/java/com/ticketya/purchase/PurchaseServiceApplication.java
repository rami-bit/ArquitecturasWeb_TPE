package com.ticketya.purchase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

/**
 * Punto de entrada de Purchase Service: orquestador de la saga de compra,
 * expuesto en la red interna (por defecto en el puerto 8084) y publicado por
 * el API Gateway en `/api/orders/**`.
 */
@SpringBootApplication
public class PurchaseServiceApplication {
    @Bean Clock clock() { return Clock.systemUTC(); }
    public static void main(String[] args) { SpringApplication.run(PurchaseServiceApplication.class, args); }
}
