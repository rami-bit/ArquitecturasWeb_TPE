package com.ticketya.payment.api.error;

import java.net.URI;

public record Problem(URI type, String title, int status, String detail,
                      URI instance, String correlationId) {
}
