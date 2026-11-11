package com.ticketya.payment.api.error;

import java.net.URI;

/**
 * Cuerpo de error conforme a {@code application/problem+json}. Nunca expone
 * trazas, credenciales ni detalles internos de la base de datos.
 */
public record Problem(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String correlationId) {
}
