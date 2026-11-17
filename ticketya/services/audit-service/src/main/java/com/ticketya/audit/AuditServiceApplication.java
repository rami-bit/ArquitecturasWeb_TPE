package com.ticketya.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Audit Service: bitácora centralizada de operaciones críticas (compra,
 * devolución, cancelación e ingreso al evento). Recibe eventos fire-and-forget
 * de los demás microservicios por la red interna y permite al ADMIN seguir una
 * operación completa por su {@code X-Correlation-Id}.
 */
@SpringBootApplication
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
