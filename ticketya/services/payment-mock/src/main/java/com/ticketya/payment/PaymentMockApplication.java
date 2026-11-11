package com.ticketya.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de Payment Mock: pasarela de pagos simulada, interna,
 * expuesta sólo en la red de servicios (por defecto en el puerto 8085).
 */
@SpringBootApplication
public class PaymentMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentMockApplication.class, args);
    }
}
