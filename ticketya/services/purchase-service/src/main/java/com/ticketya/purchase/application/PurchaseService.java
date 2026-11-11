package com.ticketya.purchase.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketya.purchase.api.error.ApiProblemException;
import com.ticketya.purchase.domain.model.OrderItem;
import com.ticketya.purchase.domain.model.OrderRecord;
import com.ticketya.purchase.domain.model.OrderStatus;
import com.ticketya.purchase.domain.model.OrderTicket;
import com.ticketya.purchase.domain.model.OrderUnit;
import com.ticketya.purchase.infrastructure.client.CatalogClient;
import com.ticketya.purchase.infrastructure.client.InventoryClient;
import com.ticketya.purchase.infrastructure.client.PaymentClient;
import com.ticketya.purchase.infrastructure.client.ServiceException;
import com.ticketya.purchase.infrastructure.client.TicketClient;
import com.ticketya.purchase.infrastructure.persistence.JdbcPurchaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;

/**
 * Orquestador de la saga de compra síncrona y persistida.
 * Secuencia: snapshot de Catalog → validación y precio de ítems → límite atómico
 * por cliente/evento → reserva en Inventory → cobro en Payment → confirmación de
 * inventario → emisión idempotente de tickets por ítem.
 */
@Service
public class PurchaseService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final PaymentClient payment;
    private final TicketClient ticket;
    private final JdbcPurchaseRepository repository;
    private final int ticketLimit;
    private final int holdTtl;

    public PurchaseService(JdbcTemplate jdbc, ObjectMapper mapper, CatalogClient catalog,
                           InventoryClient inventory, PaymentClient payment, TicketClient ticket,
                           JdbcPurchaseRepository repository,
                           @Value("${purchase.ticket-limit:6}") int ticketLimit,
                           @Value("${purchase.hold-ttl-seconds:600}") int holdTtl) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.catalog = catalog;
        this.inventory = inventory;
        this.payment = payment;
        this.ticket = ticket;
        this.repository = repository;
        this.ticketLimit = ticketLimit;
        this.holdTtl = holdTtl;
    }

    // ======================================================================== POST /api/orders

    public ResponseEntity<JsonNode> createOrder(UUID customerId, String role, String idempotencyKey,
                                                String correlationId, String rawBody) {
        JsonNode body = parseBody(rawBody);
        UUID eventId = requiredUuid(body, "eventId", "eventId");

        List<ItemInput> itemInputs = validateItems(body);
        JsonNode paymentNode = body.path("payment");
        String method = requiredText(paymentNode, "method", "medio de pago");
        String token = requiredText(paymentNode, "token", "token de pago");
        if (!List.of("CARD", "WALLET", "CASH").contains(method)) {
            throw ApiProblemException.badRequest("Medio de pago inválido: " + method);
        }
        if (!List.of("tok_approved", "tok_rejected", "tok_pending").contains(token)) {
            throw ApiProblemException.badRequest("Token de simulación inválido: " + token);
        }

        String fingerprint = sha256(rawBody);

        // Idempotencia: misma clave y mismo contenido devuelven la orden existente.
        Optional<OrderRecord> existing = repository.findOrderByKeyAndCustomer(customerId, idempotencyKey);
        if (existing.isPresent()) {
            OrderRecord order = existing.get();
            if (!order.requestFingerprint().equals(fingerprint)) {
                throw ApiProblemException.conflict("Idempotency-Key reutilizada con contenido distinto");
            }
            return ResponseEntity.ok(buildOrderResponse(order.id(), correlationId));
        }

        // Snapshot de compra de Catalog: valida que el evento sea comprable y da los precios.
        JsonNode snapshot;
        try {
            snapshot = catalog.getPurchaseSnapshot(eventId, correlationId);
        } catch (ServiceException e) {
            if (e.status() != null && e.status().value() == 404) {
                throw ApiProblemException.notFound("Evento no comprable");
            }
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                    "Catalog no disponible: " + e.getMessage());
        }

        List<PreparedItem> prepared = priceItems(itemInputs, snapshot);
        BigDecimal total = prepared.stream()
                .map(PreparedItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int units = prepared.stream().mapToInt(PreparedItem::quantity).sum();

        // Límite atómico por cliente y evento (se libera al rechazar o compensar).
        acquireLimit(customerId, eventId, units);

        // Datos del snapshot para persistir como historial de la orden.
        String eventName = snapshot.path("name").asText("Evento");
        OffsetDateTime eventStartsAt = parseOffsetDateTime(snapshot.path("startsAt").asText(null));
        String venueName = snapshot.path("venue").path("name").asText("");
        String venueCity = snapshot.path("venue").path("city").asText("");

        // Orden e ítems en estado de trabajo (el pago aún no se conoce).
        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OrderRecord order = new OrderRecord(orderId, customerId, eventId, eventName, eventStartsAt,
                venueName, venueCity, "ARS", total, OrderStatus.PAYMENT_PENDING.name(),
                null, null, method, null, null, idempotencyKey, fingerprint, null, null, now, now);
        repository.insertOrder(order);
        for (PreparedItem item : prepared) {
            UUID itemId = item.id();
            repository.insertOrderItem(new OrderItem(itemId, orderId, item.sectorId(), item.sourceSectorId(),
                    item.sectorName(), item.sectorType(), item.quantity(), item.unitPrice(), item.subtotal()));
            for (int i = 0; i < item.quantity(); i++) {
                UUID unitId = UUID.randomUUID();
                repository.insertOrderUnit(new OrderUnit(unitId, itemId, i + 1,
                        at(item.seatIds(), i),
                        at(item.sourceSeatIds(), i),
                        at(item.rowLabels(), i),
                        at(item.seatNumbers(), i)));
            }
        }

        // --- Reservar en Inventory ---
        UUID reservationId;
        OffsetDateTime expiresAt;
        try {
            JsonNode reservation = inventory.reserve(orderId, eventId, holdTtl, reservationItems(prepared), correlationId);
            reservationId = UUID.fromString(reservation.path("reservationId").asText());
            expiresAt = parseOffsetDateTime(reservation.path("expiresAt").asText(null));
        } catch (ServiceException e) {
            repository.releaseLimit(customerId, eventId, units);
            if (e.status() != null && e.status().value() == 409) {
                throw ApiProblemException.conflict("No hay disponibilidad para los ítems solicitados");
            }
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                    "Inventory no disponible: " + e.getMessage());
        }
        repository.updateOrderReservation(orderId, reservationId, expiresAt);

        // --- Cobrar en Payment ---
        JsonNode paymentResult;
        try {
            paymentResult = payment.charge(orderId, total, "ARS", method, token, correlationId);
        } catch (ServiceException e) {
            release(customerId, eventId, units, reservationId, correlationId);
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                    "Payment no disponible: " + e.getMessage());
        }
        String paymentStatus = paymentResult.path("status").asText();
        UUID paymentId = UUID.fromString(paymentResult.path("paymentId").asText());
        BigDecimal payAmount = toBigDecimal(paymentResult.path("amount"));
        OffsetDateTime payCreated = parseOffsetDateTime(paymentResult.path("createdAt").asText(null));
        OffsetDateTime payUpdated = parseOffsetDateTime(paymentResult.path("updatedAt").asText(null));
        repository.updateOrderPayment(orderId, paymentId, paymentStatus, "ARS", payAmount, method, payCreated, payUpdated);

        switch (paymentStatus) {
            case "REJECTED" -> {
                release(customerId, eventId, units, reservationId, correlationId);
                repository.updateOrderStatus(orderId, OrderStatus.REJECTED.name(), "REJECTED", null);
                throw ApiProblemException.paymentRequired(orderId, "Pago rechazado por la pasarela simulada");
            }
            case "PENDING" -> {
                // Pago incierto: se conserva la reserva y el límite; orden reconciliable.
                repository.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name(), "PENDING", expiresAt);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildOrderResponse(orderId, correlationId));
            }
            case "APPROVED" -> {
                try {
                    inventory.confirm(reservationId, correlationId);
                } catch (ServiceException e) {
                    if (e.status() != null && e.status().value() == 409) {
                        compensate(orderId, customerId, eventId, units, paymentId, reservationId, correlationId);
                        throw ApiProblemException.compensated(orderId,
                                "Pago aprobado pero la reserva ya no puede confirmarse; orden compensada");
                    }
                    try {
                        compensate(orderId, customerId, eventId, units, paymentId, reservationId, correlationId);
                    } catch (Exception ignored) {
                    }
                    throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                            "Inventory no disponible al confirmar: " + e.getMessage());
                }
                repository.commitLimit(customerId, eventId, units);
                try {
                    for (PreparedItem item : prepared) {
                        OrderRecord current = repository.findOrder(orderId)
                                .orElseThrow(() -> new IllegalStateException("Orden desaparecida: " + orderId));
                        OrderItem itemRow = repository.findItemsByOrder(orderId).stream()
                                .filter(i -> i.id().equals(item.id())).findFirst()
                                .orElseThrow(() -> new IllegalStateException("Ítem desaparecido: " + item.id()));
                        emitTickets(current, itemRow, correlationId);
                    }
                } catch (ServiceException e) {
                    // Emisión fallida: orden pagada pero con tickets pendientes (reconciliable).
                    repository.updateOrderStatus(orderId, OrderStatus.PAID_PENDING_TICKETS.name(), "APPROVED", null);
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildOrderResponse(orderId, correlationId));
                }
                repository.updateOrderStatus(orderId, OrderStatus.CONFIRMED.name(), "APPROVED", null);
                return ResponseEntity.status(HttpStatus.CREATED).body(buildOrderResponse(orderId, correlationId));
            }
            default -> throw new IllegalStateException("Estado de pago inesperado: " + paymentStatus);
        }
    }

    // ======================================================================== GET /api/orders

    public ResponseEntity<JsonNode> listOrders(UUID customerId, String role, Integer page, Integer size,
                                               String status, String correlationId) {
        boolean admin = "ADMIN".equals(role);
        if (page < 0) {
            throw ApiProblemException.badRequest("page no puede ser negativo");
        }
        if (size < 1 || size > 50) {
            throw ApiProblemException.badRequest("size debe estar entre 1 y 50");
        }
        if (status != null && !OrderStatus.isValid(status)) {
            throw ApiProblemException.badRequest("status inválido");
        }

        List<OrderRecord> orders = repository.listOrders(customerId, admin, status, page, size);
        int total = repository.countOrders(customerId, admin, status);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);

        ArrayNode items = mapper.createArrayNode();
        for (OrderRecord order : orders) {
            items.add(buildOrderResponse(order.id(), correlationId));
        }

        ObjectNode pageNode = mapper.createObjectNode();
        pageNode.set("items", items);
        pageNode.put("page", page);
        pageNode.put("size", size);
        pageNode.put("totalElements", total);
        pageNode.put("totalPages", totalPages);
        return ResponseEntity.ok(pageNode);
    }

    // ======================================================================== GET /api/orders/{orderId}

    public ResponseEntity<JsonNode> getOrder(UUID orderId, UUID customerId, String role, String correlationId) {
        boolean admin = "ADMIN".equals(role);
        OrderRecord order = repository.findOrder(orderId)
                .orElseThrow(() -> ApiProblemException.notFound("Orden no encontrada"));
        if (!admin && !order.customerId().equals(customerId)) {
            throw ApiProblemException.notFound("Orden no encontrada");
        }
        return ResponseEntity.ok(buildOrderResponse(orderId, correlationId));
    }

    // ======================================================================== POST /api/orders/{orderId}/reconcile

    public ResponseEntity<JsonNode> reconcileOrder(UUID orderId, UUID customerId, String role, String correlationId) {
        boolean admin = "ADMIN".equals(role);
        OrderRecord order = repository.findOrder(orderId)
                .orElseThrow(() -> ApiProblemException.notFound("Orden no encontrada"));
        if (!admin && !order.customerId().equals(customerId)) {
            throw ApiProblemException.notFound("Orden no encontrada");
        }

        OrderStatus status = OrderStatus.valueOf(order.status());
        if (status.isTerminal()) {
            throw ApiProblemException.conflict("Orden no reconciliable: " + order.status());
        }

        // Consultar el pago sin volver a cobrar.
        JsonNode paymentResult;
        try {
            paymentResult = payment.getByOrder(orderId, correlationId);
        } catch (ServiceException e) {
            if (e.status() != null && e.status().value() == 404) {
                return ResponseEntity.ok(buildOrderResponse(orderId, correlationId));
            }
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                    "Payment no disponible: " + e.getMessage());
        }

        String paymentStatus = paymentResult.path("status").asText();
        UUID paymentId = paymentResult.path("paymentId").asText(null) == null ? null
                : UUID.fromString(paymentResult.path("paymentId").asText());

        switch (paymentStatus) {
            case "REJECTED" -> {
                release(order.customerId(), order.eventId(), unitsOf(orderId), order.reservationId(), correlationId);
                repository.updateOrderStatus(orderId, OrderStatus.REJECTED.name(), "REJECTED", null);
                return ResponseEntity.ok(buildOrderResponse(orderId, correlationId));
            }
            case "APPROVED" -> {
                int units = unitsOf(orderId);
                try {
                    inventory.confirm(order.reservationId(), correlationId);
                } catch (ServiceException e) {
                    if (e.status() != null && e.status().value() == 409) {
                        try {
                            payment.refund(paymentId, correlationId);
                        } catch (Exception ignored) {
                        }
                        try {
                            inventory.release(order.reservationId(), correlationId);
                        } catch (Exception ignored) {
                        }
                        repository.releaseLimit(order.customerId(), order.eventId(), units);
                        repository.updateOrderStatus(orderId, OrderStatus.COMPENSATED.name(), "REFUNDED", null);
                        return ResponseEntity.ok(buildOrderResponse(orderId, correlationId));
                    }
                    throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                            "Inventory no disponible al confirmar: " + e.getMessage());
                }
                repository.commitLimit(order.customerId(), order.eventId(), units);
                try {
                    emitPendingTickets(orderId, correlationId);
                } catch (ServiceException e) {
                    repository.updateOrderStatus(orderId, OrderStatus.PAID_PENDING_TICKETS.name(), "APPROVED", null);
                    return ResponseEntity.ok(buildOrderResponse(orderId, correlationId));
                }
                repository.updateOrderStatus(orderId, OrderStatus.CONFIRMED.name(), "APPROVED", null);
                return ResponseEntity.ok(buildOrderResponse(orderId, correlationId));
            }
            case "PENDING" -> {
                // Pago sigue incierto: no se cobra de nuevo, se conserva el estado.
                return ResponseEntity.ok(buildOrderResponse(orderId, correlationId));
            }
            default -> throw new IllegalStateException("Estado de pago inesperado: " + paymentStatus);
        }
    }

    // ======================================================================== construción de respuesta

    private JsonNode buildOrderResponse(UUID orderId, String correlationId) {
        OrderRecord order = repository.findOrder(orderId)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada: " + orderId));

        ObjectNode node = mapper.createObjectNode();
        node.put("orderId", order.id().toString());
        node.put("status", order.status());
        node.put("customerId", order.customerId().toString());
        node.put("eventId", order.eventId().toString());
        node.put("eventName", order.eventName());
        node.set("eventStartsAt", textOrNull(order.eventStartsAt() == null ? null : order.eventStartsAt().toString()));
        node.put("venueName", order.venueName());
        node.put("venueCity", order.venueCity());
        node.put("currency", order.currency());
        node.put("totalAmount", order.totalAmount() == null ? 0.0 : order.totalAmount().doubleValue());
        node.set("reservationExpiresAt",
                textOrNull(order.reservationExpiresAt() == null ? null : order.reservationExpiresAt().toString()));
        node.set("createdAt", textOrNull(order.createdAt() == null ? null : order.createdAt().toString()));
        node.set("updatedAt", textOrNull(order.updatedAt() == null ? null : order.updatedAt().toString()));

        ObjectNode payment = mapper.createObjectNode();
        payment.set("paymentId", textOrNull(order.paymentId() == null ? null : order.paymentId().toString()));
        payment.put("status", order.paymentStatus() == null ? "" : order.paymentStatus());
        payment.put("amount", order.totalAmount() == null ? 0.0 : order.totalAmount().doubleValue());
        payment.put("currency", order.currency());
        payment.put("method", order.paymentMethod() == null ? "" : order.paymentMethod());
        payment.set("createdAt", textOrNull(order.paymentCreatedAt() == null ? null : order.paymentCreatedAt().toString()));
        payment.set("updatedAt", textOrNull(order.paymentUpdatedAt() == null ? null : order.paymentUpdatedAt().toString()));
        node.set("payment", payment);

        ArrayNode items = mapper.createArrayNode();
        for (OrderItem item : repository.findItemsByOrder(orderId)) {
            ObjectNode itemNode = mapper.createObjectNode();
            itemNode.put("orderItemId", item.id().toString());
            itemNode.put("sectorId", item.inventorySectorId().toString());
            itemNode.put("sourceSectorId", item.sourceSectorId().toString());
            itemNode.put("sectorName", item.sectorName());
            itemNode.put("sectorType", item.sectorType());
            itemNode.put("quantity", item.quantity());
            itemNode.put("unitPrice", item.unitPrice().doubleValue());
            itemNode.put("subtotal", item.subtotal().doubleValue());

            ArrayNode units = mapper.createArrayNode();
            for (OrderUnit unit : repository.findUnitsByItem(item.id())) {
                ObjectNode unitNode = mapper.createObjectNode();
                unitNode.put("unitNumber", unit.unitNumber());
                unitNode.set("seatId", textOrNull(unit.inventorySeatId() == null ? null : unit.inventorySeatId().toString()));
                unitNode.set("sourceSeatId",
                        textOrNull(unit.sourceSeatId() == null ? null : unit.sourceSeatId().toString()));
                unitNode.set("row", textOrNull(unit.rowLabel()));
                unitNode.set("number", numberOrNull(unit.seatNumber()));
                units.add(unitNode);
            }
            itemNode.set("units", units);

            ArrayNode tickets = mapper.createArrayNode();
            for (OrderTicket ticket : repository.findTicketsByItem(item.id())) {
                ObjectNode ticketNode = mapper.createObjectNode();
                ticketNode.put("ticketId", ticket.ticketId().toString());
                ticketNode.put("unitNumber", ticket.unitNumber());
                ticketNode.put("status", ticket.status());
                ticketNode.put("qrPayload", ticket.qrPayload() == null ? "" : ticket.qrPayload());
                ticketNode.put("qrPngBase64", ticket.qrPngBase64() == null ? "" : ticket.qrPngBase64());
                ticketNode.set("row", textOrNull(ticket.rowLabel()));
                ticketNode.set("seatNumber", numberOrNull(ticket.seatNumber()));
                tickets.add(ticketNode);
            }
            itemNode.set("tickets", tickets);
            items.add(itemNode);
        }
        node.set("items", items);
        return node;
    }

    // ======================================================================== emisión de tickets

    private void emitTickets(OrderRecord order, OrderItem item, String correlationId) {
        List<Map<String, Object>> ticketUnits = new ArrayList<>();
        for (OrderUnit unit : repository.findUnitsByItem(item.id())) {
            Map<String, Object> unitNode = new LinkedHashMap<>();
            unitNode.put("unitNumber", unit.unitNumber());
            if (unit.rowLabel() != null) {
                unitNode.put("rowLabel", unit.rowLabel());
            }
            if (unit.seatNumber() != null) {
                unitNode.put("seatNumber", unit.seatNumber());
            }
            ticketUnits.add(unitNode);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", order.customerId().toString());
        body.put("eventId", order.eventId().toString());
        body.put("eventName", order.eventName());
        body.put("eventStartsAt", order.eventStartsAt() == null ? "" : order.eventStartsAt().toString());
        body.put("venueName", order.venueName());
        body.put("venueCity", order.venueCity());
        body.put("sectorName", item.sectorName());
        body.put("tickets", ticketUnits);

        JsonNode result = ticket.issue(order.id(), item.id(), body, correlationId);
        ArrayNode tickets = (ArrayNode) result.path("tickets");
        for (JsonNode ticketNode : tickets) {
            repository.insertOrderTicket(new com.ticketya.purchase.domain.model.OrderTicket(
                    UUID.randomUUID(), order.id(), item.id(),
                    UUID.fromString(ticketNode.path("ticketId").asText()),
                    ticketNode.path("unitNumber").asInt(),
                    ticketNode.path("status").asText("ISSUED"),
                    ticketNode.path("qrPayload").asText(null),
                    ticketNode.path("qrPngBase64").asText(null),
                    ticketNode.path("rowLabel").asText(null),
                    ticketNode.path("seatNumber").asInt(0)));
        }
    }

    private void emitPendingTickets(UUID orderId, String correlationId) {
        OrderRecord order = repository.findOrder(orderId)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada: " + orderId));
        for (OrderItem item : repository.findItemsByOrder(orderId)) {
            emitTickets(order, item, correlationId);
        }
    }

    // ======================================================================== compensaciones y liberación

    private void release(UUID customerId, UUID eventId, int units, UUID reservationId, String correlationId) {
        try {
            inventory.release(reservationId, correlationId);
        } catch (Exception ignored) {
        }
        repository.releaseLimit(customerId, eventId, units);
    }

    private void compensate(UUID orderId, UUID customerId, UUID eventId, int units, UUID paymentId,
                            UUID reservationId, String correlationId) {
        try {
            payment.refund(paymentId, correlationId);
        } catch (Exception ignored) {
        }
        try {
            inventory.release(reservationId, correlationId);
        } catch (Exception ignored) {
        }
        repository.releaseLimit(customerId, eventId, units);
        repository.updateOrderStatus(orderId, OrderStatus.COMPENSATED.name(), "REFUNDED", null);
    }

    // ======================================================================== helpers de acceso a datos

    private int unitsOf(UUID orderId) {
        return repository.findItemsByOrder(orderId).stream().mapToInt(OrderItem::quantity).sum();
    }

    private static <T> T at(List<T> list, int index) {
        return list != null && index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private JsonNode textOrNull(String value) {
        return value == null ? nullNode() : textNode(value);
    }

    private JsonNode nullNode() {
        return mapper.getNodeFactory().nullNode();
    }

    private JsonNode textNode(String value) {
        return mapper.getNodeFactory().textNode(value);
    }

    private JsonNode numberOrNull(Integer value) {
        return value == null ? nullNode() : mapper.getNodeFactory().numberNode(value);
    }

    // ======================================================================== validación del request

    private record ItemInput(UUID sectorId, List<UUID> seatIds, Integer quantity) {
    }

    private record PreparedItem(UUID id, UUID sectorId, UUID sourceSectorId, String sectorName,
                                String sectorType, BigDecimal unitPrice, int quantity, BigDecimal subtotal,
                                List<UUID> seatIds, List<UUID> sourceSeatIds, List<String> rowLabels,
                                List<Integer> seatNumbers) {
    }

    private JsonNode parseBody(String rawBody) {
        try {
            return mapper.readTree(rawBody);
        } catch (Exception e) {
            throw ApiProblemException.badRequest("Cuerpo de solicitud inválido");
        }
    }

    private UUID requiredUuid(JsonNode node, String field, String label) {
        String text = node.path(field).asText(null);
        if (text == null || text.isBlank()) {
            throw ApiProblemException.badRequest(field + " es obligatorio");
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            throw ApiProblemException.badRequest(label + " debe ser un UUID válido");
        }
    }

    private String requiredText(JsonNode node, String field, String label) {
        String text = node.path(field).asText(null);
        if (text == null || text.isBlank()) {
            throw ApiProblemException.badRequest(field + " es obligatorio");
        }
        return text.trim();
    }

    private List<ItemInput> validateItems(JsonNode body) {
        JsonNode itemsNode = body.path("items");
        if (itemsNode.isMissingNode() || !itemsNode.isArray() || itemsNode.isEmpty()) {
            throw ApiProblemException.badRequest("items es obligatorio y debe tener al menos un ítem");
        }
        List<ItemInput> inputs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode itemNode : itemsNode) {
            UUID sectorId = requiredUuid(itemNode, "sectorId", "sectorId");
            boolean hasSeats = itemNode.hasNonNull("seatIds") && itemNode.path("seatIds").isArray()
                    && itemNode.path("seatIds").size() > 0;
            boolean hasQty = itemNode.hasNonNull("quantity");
            if (hasSeats == hasQty) {
                throw ApiProblemException.badRequest(
                        "Cada ítem debe informar exactamente uno de seatIds o quantity");
            }
            List<UUID> seatIds = null;
            if (hasSeats) {
                seatIds = new ArrayList<>();
                for (JsonNode seatNode : itemNode.path("seatIds")) {
                    String seatText = seatNode.asText(null);
                    if (seatText == null || seatText.isBlank()) {
                        throw ApiProblemException.badRequest("Asiento inválido");
                    }
                    try {
                        seatIds.add(UUID.fromString(seatText.trim()));
                    } catch (IllegalArgumentException e) {
                        throw ApiProblemException.badRequest("Asiento no es un UUID válido: " + seatText);
                    }
                }
            }
            Integer quantity = null;
            if (hasQty) {
                quantity = itemNode.path("quantity").asInt();
                if (quantity < 1) {
                    throw ApiProblemException.badRequest("quantity debe ser un entero positivo");
                }
            }
            String dedupeKey = sectorId + "|" + (seatIds == null ? "qty:" + quantity : seatIds);
            if (!seen.add(dedupeKey)) {
                throw ApiProblemException.badRequest("Ítem duplicado: " + sectorId);
            }
            inputs.add(new ItemInput(sectorId, seatIds, quantity));
        }
        return inputs;
    }

    private List<PreparedItem> priceItems(List<ItemInput> inputs, JsonNode snapshot) {
        Map<UUID, JsonNode> sectorsBySource = new LinkedHashMap<>();
        snapshot.path("sectors").forEach(sector -> {
            try {
                sectorsBySource.put(UUID.fromString(sector.path("sourceSectorId").asText()), sector);
            } catch (IllegalArgumentException ignored) {
            }
        });

        List<PreparedItem> prepared = new ArrayList<>();
        for (ItemInput input : inputs) {
            JsonNode sector = sectorsBySource.get(input.sectorId());
            if (sector == null) {
                throw ApiProblemException.badRequest("Sector desconocido: " + input.sectorId());
            }
            String sectorType = sector.path("type").asText();
            BigDecimal unitPrice = new BigDecimal(sector.path("price").asText("0"));

            List<UUID> seatIds = new ArrayList<>();
            List<UUID> sourceSeatIds = new ArrayList<>();
            List<String> rowLabels = new ArrayList<>();
            List<Integer> seatNumbers = new ArrayList<>();
            int quantity;

            if (input.seatIds() != null && !input.seatIds().isEmpty()) {
                if (!"NUMBERED".equals(sectorType)) {
                    throw ApiProblemException.badRequest(
                            "Asientos solo permitidos en un sector numerado: " + input.sectorId());
                }
                for (UUID seatId : input.seatIds()) {
                    boolean found = false;
                    for (JsonNode seat : sector.path("seats")) {
                        if (seat.path("sourceSeatId").asText().equals(seatId.toString())) {
                            found = true;
                            seatIds.add(seatId);
                            sourceSeatIds.add(seatId);
                            rowLabels.add(seat.path("row").asText(null));
                            seatNumbers.add(seat.path("number").asInt(0));
                            break;
                        }
                    }
                    if (!found) {
                        throw ApiProblemException.badRequest(
                                "Asiento no pertenece al sector solicitado: " + seatId);
                    }
                }
                quantity = seatIds.size();
            } else {
                if (!"GENERAL_ADMISSION".equals(sectorType)) {
                    throw ApiProblemException.badRequest(
                            "Cantidad solo permitida en un sector de admisión general: " + input.sectorId());
                }
                quantity = input.quantity();
            }

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            prepared.add(new PreparedItem(UUID.randomUUID(), input.sectorId(),
                    UUID.fromString(sector.path("sourceSectorId").asText()),
                    sector.path("name").asText(), sectorType, unitPrice, quantity, subtotal,
                    seatIds, sourceSeatIds, rowLabels, seatNumbers));
        }
        return prepared;
    }

    private List<Map<String, Object>> reservationItems(List<PreparedItem> prepared) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (PreparedItem item : prepared) {
            if (!item.seatIds().isEmpty()) {
                for (UUID seatId : item.seatIds()) {
                    Map<String, Object> itemNode = new LinkedHashMap<>();
                    itemNode.put("sectorId", item.sectorId().toString());
                    itemNode.put("seatId", seatId.toString());
                    items.add(itemNode);
                }
            } else {
                Map<String, Object> itemNode = new LinkedHashMap<>();
                itemNode.put("sectorId", item.sectorId().toString());
                itemNode.put("quantity", item.quantity());
                items.add(itemNode);
            }
        }
        return items;
    }

    // ======================================================================== límite atómico (bloqueo transaccional)

    private void acquireLimit(UUID customerId, UUID eventId, int units) {
        String lockKey = customerId.toString() + "|" + eventId;
        try (var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var ps = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                ps.setString(1, lockKey);
                ps.execute();
            }
            int committed = 0;
            int pending = 0;
            try (var ps = connection.prepareStatement(
                    "SELECT committed_units, pending_units FROM customer_event_limits "
                            + "WHERE customer_id = ? AND event_id = ? FOR UPDATE")) {
                ps.setObject(1, customerId);
                ps.setObject(2, eventId);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        committed = rs.getInt("committed_units");
                        pending = rs.getInt("pending_units");
                    }
                }
            }
            if (committed + pending + units > ticketLimit) {
                connection.rollback();
                throw ApiProblemException.conflict(
                        "Límite de entradas por cliente y evento excedido (máximo " + ticketLimit + ")");
            }
            try (var ps = connection.prepareStatement("""
                    INSERT INTO customer_event_limits (id, customer_id, event_id, committed_units, pending_units)
                    VALUES (?, ?, ?, 0, ?)
                    ON CONFLICT (customer_id, event_id)
                    DO UPDATE SET pending_units = customer_event_limits.pending_units + ?""")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, customerId);
                ps.setObject(3, eventId);
                ps.setInt(4, units);
                ps.setInt(5, units);
                ps.executeUpdate();
            }
            connection.commit();
        } catch (ApiProblemException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Error gestionando el límite de compra", e);
        }
    }

    // ======================================================================== utilidades

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el fingerprint de la solicitud", e);
        }
    }

    private OffsetDateTime parseOffsetDateTime(String text) {
        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return OffsetDateTime.parse(text);
    }

    private BigDecimal toBigDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(node.asText());
    }
}
