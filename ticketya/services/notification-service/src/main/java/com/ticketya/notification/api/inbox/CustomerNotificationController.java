package com.ticketya.notification.api.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.notification.api.error.ApiProblemException;
import com.ticketya.notification.infrastructure.JdbcNotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Bandeja del cliente autenticado, publicada por el Gateway en
 * {@code /api/notifications/**}. La identidad se toma de {@code X-User-Id}
 * derivado del JWT: no hay {@code customerId} en la ruta.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Customer notifications", description = "Notificaciones enviadas al cliente autenticado")
public class CustomerNotificationController {
    private final JdbcNotificationRepository repository;

    public CustomerNotificationController(JdbcNotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    @Operation(operationId = "listMyNotifications", summary = "Listar mis notificaciones (más recientes primero)")
    @ApiResponse(responseCode = "200", description = "Página de notificaciones")
    @ApiResponse(responseCode = "400", description = "Paginación o tipo inválidos")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT o identidad inválida")
    public NotificationPage mine(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        if (!"CLIENT".equals(role)) {
            throw ApiProblemException.forbidden("CLIENT role required");
        }
        UUID customerId = identity(userId);
        if (page < 0 || size < 1 || size > 50) {
            throw ApiProblemException.badRequest("page must be >= 0 and size between 1 and 50");
        }
        if (type != null && !type.isBlank() && !type.matches("[A-Z_]{1,40}")) {
            throw ApiProblemException.badRequest("type is invalid");
        }
        return new NotificationPage(repository.findForCustomer(customerId, type, page, size), page, size,
                repository.countForCustomer(customerId, type));
    }

    public record EmailContact(String email) {
    }

    @GetMapping("/me/email")
    @Operation(operationId = "getMyNotificationEmail", summary = "Consultar mi email de contacto")
    @ApiResponse(responseCode = "200", description = "Email propio o nulo si no está registrado")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT o identidad inválida")
    public EmailContact email(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return new EmailContact(repository.emailForCustomer(clientId(userId, role)).orElse(null));
    }

    @PutMapping("/me/email")
    @Operation(operationId = "putMyNotificationEmail", summary = "Registrar mi email de contacto")
    @ApiResponse(responseCode = "200", description = "Email registrado")
    @ApiResponse(responseCode = "400", description = "Email inválido o campo ajeno")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT o identidad inválida")
    public EmailContact putEmail(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody JsonNode body) {
        UUID customer = clientId(userId, role);
        if (body == null || !body.isObject() || body.size() != 1 || !body.path("email").isTextual()) {
            throw ApiProblemException.badRequest("body must contain only email");
        }
        String email = body.path("email").asText().trim();
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw ApiProblemException.badRequest("email is invalid");
        }
        repository.upsertEmail(customer, email);
        return new EmailContact(email);
    }

    private static UUID clientId(String userId, String role) {
        if (!"CLIENT".equals(role)) {
            throw ApiProblemException.forbidden("CLIENT role required");
        }
        return identity(userId);
    }

    private static UUID identity(String userId) {
        try {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(userId.trim());
        } catch (IllegalArgumentException invalid) {
            throw ApiProblemException.forbidden("A valid identity is required");
        }
    }
}
