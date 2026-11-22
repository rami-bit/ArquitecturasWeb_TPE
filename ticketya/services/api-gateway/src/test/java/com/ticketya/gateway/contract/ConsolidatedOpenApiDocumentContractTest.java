package com.ticketya.gateway.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida la completitud del contrato versionado consolidado {@code docs/openapi.yaml}
 * de la Entrega 1, sin levantar Spring ni Docker y sin depender del formato u orden
 * del YAML. Protege la superficie completa E1: Catalog (venues, gestion de eventos,
 * busqueda/detalle y snapshots internos), Inventory (disponibilidad, reservas,
 * cancelacion de evento y liberacion de unidades vendidas), Purchase (ordenes,
 * devoluciones y cancelacion de evento), Ticket (emision y revocacion de QR) y
 * Payment Mock (cobros, consultas y reintegros total/parcial). Ademas exige
 * {@code operationId} presentes y unicos en TODO el documento, asegura que las
 * reglas transversales de {@code application/problem+json} y {@code X-Correlation-Id},
 * la advertencia de que el update de evento ignora {@code offerings} y debe usarse
 * {@code /offerings}, y el modelo de seguridad de {@code add-jwt-and-role-security}:
 * esquema HTTP Bearer ({@code BearerJWT}) en {@code components.securitySchemes} y
 * requisito global heredado por las 21 operaciones protegidas (con respuesta
 * {@code 401} declarada), {@code security: []} explicito en las tres consultas
 * publicas ({@code GET /api/events}, {@code GET /api/events/{id}} y la
 * disponibilidad) y en todas las rutas {@code /internal/**}, y ausencia de
 * parametros de identidad externos ({@code X-User-Id}/{@code X-User-Role}) en
 * todo el documento: la identidad viaja unicamente como Bearer JWT y los
 * servicios la reciben derivada por el Gateway.
 */
class ConsolidatedOpenApiDocumentContractTest {

    private record Operation(String path, String method, String operationId, String owner) {
    }

    private static final String CATALOG = "Catalog";
    private static final String INVENTORY = "Inventory";
    private static final String PURCHASE = "Purchase";
    private static final String TICKET = "Ticket";
    private static final String PAYMENT = "Payment";
    private static final String PREFERENCES = "Preferences";
    private static final String NOTIFICATION = "Notification";

    private static final List<Operation> ENTREGA1_OPERATIONS = List.of(
            // ---------------------------------------------------------------- Catalog
            new Operation("/api/venues", "post", "createVenue", CATALOG),
            new Operation("/api/venues", "get", "listVenues", CATALOG),
            new Operation("/api/venues/{id}", "put", "updateVenue", CATALOG),
            new Operation("/api/management/events", "post", "createEvent", CATALOG),
            new Operation("/api/management/events", "get", "listManageableEvents", CATALOG),
            new Operation("/api/management/events/{id}", "get", "getManageableEvent", CATALOG),
            new Operation("/api/management/events/{id}", "put", "updateEventDraft", CATALOG),
            new Operation("/api/management/events/{id}/offerings", "put",
                    "replaceEventOfferings", CATALOG),
            new Operation("/api/management/events/{id}/publish", "post", "publishEvent", CATALOG),
            new Operation("/api/management/events/{id}/suspend", "post", "suspendEvent", CATALOG),
            new Operation("/api/management/events/{id}/cancel", "post",
                    "cancelEventCatalog", CATALOG),
            new Operation("/api/management/events/{id}/resume", "post", "resumeEvent", CATALOG),
            new Operation("/api/events", "get", "searchPublishedEvents", CATALOG),
            new Operation("/api/events/{id}", "get", "getPublishedEventDetail", CATALOG),
            new Operation("/internal/events/{eventId}/snapshot", "get",
                    "getCatalogEventSnapshot", CATALOG),
            new Operation("/internal/events/{eventId}/purchase-snapshot", "get",
                    "getCatalogPurchaseSnapshot", CATALOG),
            // -------------------------------------------------------------- Inventory
            new Operation("/api/inventory/events/{eventId}/availability", "get",
                    "getEventAvailability", INVENTORY),
            new Operation("/internal/reservations", "post", "createReservation", INVENTORY),
            new Operation("/internal/reservations/by-order/{orderId}", "get",
                    "getReservationByOrderId", INVENTORY),
            new Operation("/internal/reservations/{reservationId}", "get",
                    "getReservationById", INVENTORY),
            new Operation("/internal/reservations/{reservationId}/confirm", "post",
                    "confirmReservation", INVENTORY),
            new Operation("/internal/reservations/{reservationId}/release", "post",
                    "releaseReservation", INVENTORY),
            new Operation("/internal/reservations/{reservationId}/sold-units/release", "post",
                    "releaseSoldUnit", INVENTORY),
            new Operation("/internal/events/{eventId}/hold-ttl", "put",
                    "updateEventHoldTtl", INVENTORY),
            new Operation("/internal/events/{eventId}/cancellation", "put",
                    "cancelEventInventory", INVENTORY),
            // ---------------------------------------------------------------- Purchase
            new Operation("/api/orders", "post", "createOrder", PURCHASE),
            new Operation("/api/orders", "get", "listOrders", PURCHASE),
            new Operation("/api/orders/{orderId}", "get", "getOrder", PURCHASE),
            new Operation("/api/orders/{orderId}/reconcile", "post",
                    "reconcileOrder", PURCHASE),
            new Operation("/api/events/{eventId}/cancellation", "post",
                    "startEventCancellation", PURCHASE),
            new Operation("/api/events/{eventId}/cancellation", "get",
                    "getEventCancellationProgress", PURCHASE),
            new Operation("/api/refunds/tickets/{ticketId}", "post",
                    "requestTicketRefund", PURCHASE),
            new Operation("/api/refunds/{refundId}", "get", "getRefundRequest", PURCHASE),
            new Operation("/api/refunds/{refundId}/decision", "post",
                    "decideRefundRequest", PURCHASE),
            // ----------------------------------------------------------------- Ticket
            new Operation("/internal/orders/{orderId}/items/{orderItemId}/tickets", "post",
                    "issueTicketsForOrderItem", TICKET),
            new Operation("/internal/tickets/{ticketId}/revoke", "post",
                    "revokeTicket", TICKET),
            // ---------------------------------------------------------------- Payment
            new Operation("/internal/payments", "post", "createPayment", PAYMENT),
            new Operation("/internal/payments/by-order/{orderId}", "get",
                    "getPaymentByOrderId", PAYMENT),
            new Operation("/internal/payments/{paymentId}", "get",
                    "getPaymentById", PAYMENT),
            new Operation("/internal/payments/{paymentId}/refund", "post",
                    "refundPayment", PAYMENT),
            new Operation("/internal/payments/{paymentId}/refunds", "post",
                    "refundPaymentPartial", PAYMENT));

    private static final String AUDIT = "Audit";

    /**
     * Operaciones de Entrega 2 incorporadas por E2-02 (bitácora centralizada),
     * E2-03 (control de acceso QR) y E2-04 (reportes del organizador).
     */
    private static final List<Operation> ENTREGA2_OPERATIONS = List.of(
            new Operation("/api/access/events/{eventId}/validations", "post", "validateTicketAccess", TICKET),
            new Operation("/api/access/events/{eventId}/scans", "get", "listAccessScans", TICKET),
            new Operation("/api/reports/events/{eventId}", "get", "getOrganizerEventReport", PURCHASE),
            new Operation("/api/audit/events", "get", "searchAuditEvents", AUDIT),
            new Operation("/api/audit/traces/{correlationId}", "get", "getAuditTrace", AUDIT),
            new Operation("/internal/audit-events", "post", "recordAuditEvent", AUDIT));

    /**
     * Operaciones de PDF-F10 (preferencias y recomendaciones): tres públicas en
     * Preferences Service y dos contratos internos en Purchase y Catalog.
     */
    private static final List<Operation> PREFERENCES_OPERATIONS = List.of(
            new Operation("/api/customers/me/preferences", "put",
                    "replaceCustomerPreferences", PREFERENCES),
            new Operation("/api/customers/me/preferences", "get",
                    "getCustomerPreferences", PREFERENCES),
            new Operation("/api/customers/me/recommendations", "get",
                    "getCustomerRecommendations", PREFERENCES),
            new Operation("/internal/customers/{customerId}/recommendation-signals", "get",
                    "getRecommendationSignals", PURCHASE),
            new Operation("/internal/events/recommendation-candidates", "get",
                    "getRecommendationCandidates", CATALOG));

    private static final Map<String, Set<Integer>> ENTREGA2_STATUS_CODES = Map.of(
            "/api/access/events/{eventId}/validations:post", Set.of(200, 400, 401, 403, 404, 409, 422),
            "/api/access/events/{eventId}/scans:get", Set.of(200, 400, 401, 403),
            "/api/reports/events/{eventId}:get", Set.of(200, 401, 403, 404, 503),
            "/api/audit/events:get", Set.of(200, 400, 401, 403),
            "/api/audit/traces/{correlationId}:get", Set.of(200, 401, 403, 404),
            "/internal/audit-events:post", Set.of(202, 400));

    /**
     * Operaciones de PDF-F11 (notificaciones): la bandeja pública del cliente,
     * la ingesta interna de hechos y los dos contratos internos de destinatarios.
     */
    private static final List<Operation> NOTIFICATION_OPERATIONS = List.of(
            new Operation("/api/notifications/me", "get", "listMyNotifications", NOTIFICATION),
            new Operation("/api/notifications/me/email", "get", "getMyNotificationEmail", NOTIFICATION),
            new Operation("/api/notifications/me/email", "put", "putMyNotificationEmail", NOTIFICATION),
            new Operation("/api/management/events/{id}/schedule", "patch", "postponeEventSchedule", CATALOG),
            new Operation("/internal/notifications", "post", "recordOrderNotification", NOTIFICATION),
            new Operation("/internal/notifications/event-changes", "post",
                    "recordEventChangeNotification", NOTIFICATION),
            new Operation("/internal/events/{eventId}/buyers", "get", "listEventBuyers", PURCHASE),
            new Operation("/internal/preferences/matches", "get", "findPreferenceMatches", PREFERENCES));

    private static final Map<String, Set<Integer>> NOTIFICATION_STATUS_CODES = Map.of(
            "/api/notifications/me:get", Set.of(200, 400, 401, 403),
            "/api/notifications/me/email:get", Set.of(200, 401, 403),
            "/api/notifications/me/email:put", Set.of(200, 400, 401, 403),
            "/api/management/events/{id}/schedule:patch", Set.of(200, 400, 401, 403, 404, 409),
            "/internal/notifications:post", Set.of(202, 400),
            "/internal/notifications/event-changes:post", Set.of(202, 400),
            "/internal/events/{eventId}/buyers:get", Set.of(200),
            "/internal/preferences/matches:get", Set.of(200, 400));

    private static final Map<String, Set<Integer>> PREFERENCES_STATUS_CODES = Map.of(
            "/api/customers/me/preferences:put", Set.of(200, 400, 401, 403),
            "/api/customers/me/preferences:get", Set.of(200, 401, 403),
            "/api/customers/me/recommendations:get", Set.of(200, 400, 401, 403, 503),
            "/internal/customers/{customerId}/recommendation-signals:get", Set.of(200),
            "/internal/events/recommendation-candidates:get", Set.of(200, 400));

    private static final Map<String, Set<Integer>> MINIMUM_STATUS_CODES = new LinkedHashMap<>();

    static {
        MINIMUM_STATUS_CODES.put("/api/venues:post", Set.of(201, 400, 403));
        MINIMUM_STATUS_CODES.put("/api/venues:get", Set.of(200, 400, 403));
        MINIMUM_STATUS_CODES.put("/api/venues/{id}:put", Set.of(200, 400, 403, 404));
        MINIMUM_STATUS_CODES.put("/api/management/events:post", Set.of(201, 400, 403));
        MINIMUM_STATUS_CODES.put("/api/management/events:get", Set.of(200, 403));
        MINIMUM_STATUS_CODES.put("/api/management/events/{id}:get", Set.of(200, 403, 404));
        MINIMUM_STATUS_CODES.put("/api/management/events/{id}:put",
                Set.of(200, 400, 403, 404, 409));
        MINIMUM_STATUS_CODES.put("/api/management/events/{id}/offerings:put",
                Set.of(200, 400, 403, 404, 409));
        MINIMUM_STATUS_CODES.put("/api/management/events/{id}/publish:post",
                Set.of(200, 400, 403, 404, 409));
        MINIMUM_STATUS_CODES.put("/api/management/events/{id}/suspend:post",
                Set.of(200, 403, 404, 409));
        MINIMUM_STATUS_CODES.put("/api/management/events/{id}/cancel:post",
                Set.of(200, 403, 404, 409, 503));
        MINIMUM_STATUS_CODES.put("/api/management/events/{id}/resume:post",
                Set.of(200, 403, 404, 409));
        MINIMUM_STATUS_CODES.put("/api/events:get", Set.of(200, 400));
        MINIMUM_STATUS_CODES.put("/api/events/{id}:get", Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/api/inventory/events/{eventId}/availability:get",
                Set.of(200, 404, 503, 504));
        MINIMUM_STATUS_CODES.put("/internal/reservations:post",
                Set.of(201, 200, 400, 404, 409, 503));
        MINIMUM_STATUS_CODES.put("/internal/reservations/by-order/{orderId}:get",
                Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/internal/reservations/{reservationId}:get",
                Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/internal/reservations/{reservationId}/confirm:post",
                Set.of(200, 404, 409));
        MINIMUM_STATUS_CODES.put("/internal/reservations/{reservationId}/release:post",
                Set.of(200, 404, 409));
        MINIMUM_STATUS_CODES.put(
                "/internal/reservations/{reservationId}/sold-units/release:post",
                Set.of(200, 400, 409));
        MINIMUM_STATUS_CODES.put("/internal/events/{eventId}/hold-ttl:put",
                Set.of(200, 400, 404));
        MINIMUM_STATUS_CODES.put("/internal/events/{eventId}/cancellation:put",
                Set.of(204, 404));
        MINIMUM_STATUS_CODES.put("/internal/events/{eventId}/snapshot:get",
                Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/internal/events/{eventId}/purchase-snapshot:get",
                Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/internal/orders/{orderId}/items/{orderItemId}/tickets:post",
                Set.of(201, 200, 400, 409));
        MINIMUM_STATUS_CODES.put("/internal/tickets/{ticketId}/revoke:post",
                Set.of(200, 404, 409));
        MINIMUM_STATUS_CODES.put("/api/orders:post",
                Set.of(201, 200, 202, 400, 402, 403, 404, 409, 503));
        MINIMUM_STATUS_CODES.put("/api/orders:get", Set.of(200, 400, 403));
        MINIMUM_STATUS_CODES.put("/api/orders/{orderId}:get", Set.of(200, 403, 404));
        MINIMUM_STATUS_CODES.put("/api/orders/{orderId}/reconcile:post",
                Set.of(200, 403, 404, 409));
        MINIMUM_STATUS_CODES.put("/api/events/{eventId}/cancellation:post",
                Set.of(200, 202, 403, 404, 409, 503));
        MINIMUM_STATUS_CODES.put("/api/events/{eventId}/cancellation:get",
                Set.of(200, 403, 404));
        MINIMUM_STATUS_CODES.put("/api/refunds/tickets/{ticketId}:post",
                Set.of(201, 200, 404, 409, 503));
        MINIMUM_STATUS_CODES.put("/api/refunds/{refundId}:get", Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/api/refunds/{refundId}/decision:post",
                Set.of(200, 202, 400, 403, 404, 409));
        MINIMUM_STATUS_CODES.put("/internal/payments:post", Set.of(201, 200, 400, 409));
        MINIMUM_STATUS_CODES.put("/internal/payments/by-order/{orderId}:get",
                Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/internal/payments/{paymentId}:get", Set.of(200, 404));
        MINIMUM_STATUS_CODES.put("/internal/payments/{paymentId}/refund:post",
                Set.of(200, 404, 409));
        MINIMUM_STATUS_CODES.put("/internal/payments/{paymentId}/refunds:post",
                Set.of(201, 200, 400, 404, 409));
    }

    /**
     * Consultas publicas por contrato (sin Bearer): busqueda y detalle de
     * eventos publicados y disponibilidad de inventario.
     */
    private static final Set<String> PUBLIC_QUERY_KEYS = Set.of(
            "/api/events:get",
            "/api/events/{id}:get",
            "/api/inventory/events/{eventId}/availability:get");

    /** Requisito Bearer heredado del {@code security} global del documento. */
    private static final Map<String, List<String>> BEARER_REQUIREMENT = Map.of("BearerJWT", List.of());

    /**
     * Fragmentos de capacidades todavía fuera del alcance del contrato. Con
     * PDF-F11 (notificaciones) implementada, todas las funcionalidades del PDF
     * están declaradas y la lista queda vacía; el guard se conserva para
     * impedir documentar en el futuro una capacidad que ningún servicio implementa.
     */
    private static final List<String> NOT_YET_IMPLEMENTED_CAPABILITY_FRAGMENTS = List.of();

    private static Map<String, Object> contract;

    @BeforeAll
    static void loadContract() {
        Path docs = locateOpenApiDocument();
        try (Reader reader = Files.newBufferedReader(docs, StandardCharsets.UTF_8)) {
            contract = new Yaml().load(reader);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        assertThat(contract).as("docs/openapi.yaml debe ser un documento OpenAPI").isNotNull();
    }

    private static Path locateOpenApiDocument() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("docs").resolve("openapi.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "No se encontró docs/openapi.yaml subiendo desde " + System.getProperty("user.dir"));
    }

    // ------------------------------------------------------------------ acceso al doc

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths() {
        return (Map<String, Object>) contract.get("paths");
    }

    /** Todas las operaciones (path, metodo) declaradas en el documento consolidado. */
    private static Map<String, Map<String, Object>> allDocumentedOperations() {
        Map<String, Map<String, Object>> operations = new LinkedHashMap<>();
        for (Map.Entry<String, Object> pathEntry : paths().entrySet()) {
            assertThat(pathEntry.getValue())
                    .as("el path %s debe declarar operaciones", pathEntry.getKey())
                    .isInstanceOf(Map.class);
            Map<String, Object> item = (Map<String, Object>) pathEntry.getValue();
            for (Map.Entry<String, Object> methodEntry : item.entrySet()) {
                String method = methodEntry.getKey();
                if (method.startsWith("x-") || "parameters".equals(method)
                        || "summary".equals(method) || "description".equals(method)) {
                    continue;
                }
                assertThat(methodEntry.getValue())
                        .as("%s %s debe ser una operacion", method, pathEntry.getKey())
                        .isInstanceOf(Map.class);
                operations.put(pathEntry.getKey() + ":" + method,
                        (Map<String, Object>) methodEntry.getValue());
            }
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(Operation operation) {
        Map<String, Object> item = (Map<String, Object>) paths().get(operation.path());
        assertThat(item)
                .as("el path %s (%s) debe existir en el contrato",
                        operation.path(), operation.owner())
                .isNotNull();
        Map<String, Object> op = (Map<String, Object>) item.get(operation.method());
        assertThat(op)
                .as("la operacion %s de %s (%s) debe existir",
                        operation.method(), operation.path(), operation.owner())
                .isNotNull();
        return op;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responses(Operation operation) {
        return (Map<String, Object>) operation(operation).get("responses");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> components() {
        Map<String, Object> components = (Map<String, Object>) contract.get("components");
        return components == null ? Map.of() : components;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas() {
        Map<String, Object> schemas = (Map<String, Object>) components().get("schemas");
        assertThat(schemas).as("el documento declara components.schemas").isNotNull();
        return schemas;
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(String schemaName) {
        Object required = schema(schemaName).get("required");
        assertThat(required).as("%s debe declarar required", schemaName).isInstanceOf(List.class);
        return (List<String>) required;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) {
        Map<String, Object> schema = (Map<String, Object>) schemas().get(name);
        assertThat(schema).as("el schema %s debe existir", name).isNotNull();
        return schema;
    }

    private static boolean declaresParameterNamed(Map<String, Object> operation,
            String parameterName) {
        Object parameters = operation.get("parameters");
        if (!(parameters instanceof List<?> list)) {
            return false;
        }
        for (Object candidate : list) {
            if (!(candidate instanceof Map<?, ?> map)) {
                continue;
            }
            if (parameterName.equals(map.get("name"))) {
                return true;
            }
            Object ref = map.get("$ref");
            if (ref instanceof String reference
                    && reference.endsWith("/" + parameterName)) {
                return true;
            }
        }
        return false;
    }

    /** Resuelve {@code $ref} contra components.parameters; los inline pasan igual. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveParameter(Object candidate) {
        if (candidate instanceof Map<?, ?> map && map.get("$ref") instanceof String reference) {
            assertThat(reference)
                    .as("las refs de parametros apuntan a components.parameters")
                    .startsWith("#/components/parameters/");
            String name = reference.substring("#/components/parameters/".length());
            Map<String, Object> registry = (Map<String, Object>) components().get("parameters");
            assertThat(registry)
                    .as("el documento declara components.parameters")
                    .isNotNull();
            Map<String, Object> resolved = (Map<String, Object>) registry.get(name);
            assertThat(resolved)
                    .as("el parametro referenciado %s existe", reference)
                    .isNotNull();
            return resolved;
        }
        assertThat(candidate)
                .as("cada parametro es un objeto inline o una $ref")
                .isInstanceOf(Map.class);
        return (Map<String, Object>) candidate;
    }

    /** Parametros declarados para la operacion, combinando nivel de path y de operacion. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resolvedParameters(Operation operation) {
        Map<String, Object> pathItem = (Map<String, Object>) paths().get(operation.path());
        List<Object> declared = new ArrayList<>();
        Object pathLevel = pathItem.get("parameters");
        if (pathLevel instanceof List<?> pathList) {
            declared.addAll(pathList);
        }
        Object operationLevel = operation(operation).get("parameters");
        if (operationLevel instanceof List<?> operationList) {
            declared.addAll(operationList);
        }
        return declared.stream()
                .map(ConsolidatedOpenApiDocumentContractTest::resolveParameter)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Clasificacion de seguridad de una operacion: protegida o publica/interna. */
    private static boolean isPublicOrInternal(Operation operation) {
        return operation.path().startsWith("/internal/")
                || PUBLIC_QUERY_KEYS.contains(operation.path() + ":" + operation.method());
    }

    /** Requisito de seguridad efectivo: el de la operacion o el global del documento. */
    private static List<?> effectiveSecurity(Map<String, Object> operation) {
        Object security = operation.get("security") != null
                ? operation.get("security")
                : contract.get("security");
        assertThat(security)
                .as("la operacion declara security o hereda el requisito global")
                .isInstanceOf(List.class);
        return (List<?>) security;
    }

    // ------------------------------------------------------------------------ tests

    @Test
    void contractDeclaresEveryEntrega1OperationWithItsOperationIdAndExactVerbs() {
        Set<String> expectedPaths = new LinkedHashSet<>();
        Map<String, Set<String>> expectedVerbsPerPath = new LinkedHashMap<>();
        for (Operation operation : ENTREGA1_OPERATIONS) {
            expectedPaths.add(operation.path());
            expectedVerbsPerPath.computeIfAbsent(operation.path(), key -> new LinkedHashSet<>())
                    .add(operation.method());
        }

        assertThat(paths().keySet())
                .as("el contrato consolidado declara todos los paths de Entrega 1")
                .containsAll(expectedPaths);

        for (Operation operation : ENTREGA1_OPERATIONS) {
            assertThat(operation(operation).get("operationId"))
                    .as("operationId de %s %s (%s)",
                            operation.method(), operation.path(), operation.owner())
                    .isEqualTo(operation.operationId());
        }

        for (Map.Entry<String, Set<String>> expected : expectedVerbsPerPath.entrySet()) {
            Map<String, Object> item = (Map<String, Object>) paths().get(expected.getKey());
            assertThat(item.keySet().stream()
                            .filter(key -> !key.startsWith("x-"))
                            .filter(key -> !"parameters".equals(key))
                            .collect(java.util.stream.Collectors.toSet()))
                    .as("%s declara exactamente los verbos %s", expected.getKey(),
                            expected.getValue())
                    .containsExactlyInAnyOrderElementsOf(expected.getValue());
        }
    }

    @Test
    void everyOperationInTheDocumentDeclaresAUniqueOperationId() {
        Map<String, String> operationIdOwners = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry
                : allDocumentedOperations().entrySet()) {
            Object operationId = entry.getValue().get("operationId");
            if (operationId == null || operationId.toString().isBlank()) {
                missing.add(entry.getKey());
                continue;
            }
            String previous = operationIdOwners.putIfAbsent(
                    operationId.toString(), entry.getKey());
            assertThat(previous)
                    .as("operationId %s de %s debe ser unico (ya usado por %s)",
                            operationId, entry.getKey(), previous)
                    .isNull();
        }
        assertThat(missing)
                .as("toda operacion del documento declara operationId")
                .isEmpty();

        // Los operationIds esperados de Entrega 1 siguen presentes.
        assertThat(operationIdOwners.keySet())
                .as("los 41 operationIds de Entrega 1 siguen declarados")
                .containsAll(ENTREGA1_OPERATIONS.stream()
                        .map(Operation::operationId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void everyEntrega1OperationDeclaresTheMinimumStatusCodes() {
        for (Map.Entry<String, Set<Integer>> entry : MINIMUM_STATUS_CODES.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Operation operation = ENTREGA1_OPERATIONS.stream()
                    .filter(candidate -> candidate.path().equals(parts[0])
                            && candidate.method().equals(parts[1]))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "operacion no encontrada: " + entry.getKey()));
            Set<Integer> documented = responses(operation).keySet().stream()
                    .filter(code -> code.matches("\\d{3}"))
                    .map(Integer::parseInt)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(documented)
                    .as("codigos minimos de %s (%s)", entry.getKey(), operation.owner())
                    .containsAll(entry.getValue());
        }
    }

    @Test
    void everyErrorResponseOfEveryOperationDeclaresProblemJson() {
        for (Map.Entry<String, Map<String, Object>> entry
                : allDocumentedOperations().entrySet()) {
            Object responses = entry.getValue().get("responses");
            assertThat(responses)
                    .as("%s declara responses", entry.getKey())
                    .isInstanceOf(Map.class);
            for (Map.Entry<String, Object> response
                    : ((Map<String, Object>) responses).entrySet()) {
                String code = response.getKey();
                if (!code.matches("\\d{3}") || Integer.parseInt(code) < 400) {
                    continue;
                }
                Map<String, Object> body = (Map<String, Object>) response.getValue();
                Map<String, Object> content = (Map<String, Object>) body.get("content");
                assertThat(content)
                        .as("%s %s declara content", entry.getKey(), code)
                        .isNotNull();
                assertThat(content.keySet())
                        .as("%s %s declara application/problem+json", entry.getKey(), code)
                        .contains("application/problem+json");
            }
        }
    }

    @Test
    void everyOperationDeclaresCorrelationIdRequestParameterAndResponseHeaders() {
        for (Map.Entry<String, Map<String, Object>> entry
                : allDocumentedOperations().entrySet()) {
            Map<String, Object> op = entry.getValue();
            assertThat(declaresParameterNamed(op, "CorrelationId")
                            || declaresParameterNamed(op, "X-Correlation-Id"))
                    .as("%s declara el parametro X-Correlation-Id", entry.getKey())
                    .isTrue();

            Object responses = op.get("responses");
            assertThat(responses).as("%s declara responses", entry.getKey()).isNotNull();
            for (Map.Entry<String, Object> response
                    : ((Map<String, Object>) responses).entrySet()) {
                Map<String, Object> body = (Map<String, Object>) response.getValue();
                Map<String, Object> headers = (Map<String, Object>) body.get("headers");
                assertThat(headers)
                        .as("%s %s declara headers", entry.getKey(), response.getKey())
                        .isNotNull();
                assertThat(headers.keySet())
                        .as("%s %s declara el header X-Correlation-Id",
                                entry.getKey(), response.getKey())
                        .contains("X-Correlation-Id");
            }
        }
    }

    @Test
    void problemSchemaRequiresTheContractFields() {
        assertThat(required("Problem"))
                .contains("type", "title", "status", "detail", "instance", "correlationId");
    }

    @Test
    void orderCreationDeclaresTheRequiredIdempotencyKeyHeader() {
        Map<String, Object> createOrder = operation(new Operation(
                "/api/orders", "post", "createOrder", PURCHASE));
        assertThat(declaresParameterNamed(createOrder, "IdempotencyKey")
                        || declaresParameterNamed(createOrder, "Idempotency-Key"))
                .as("POST /api/orders declara el header Idempotency-Key")
                .isTrue();
    }

    @Test
    void protectedOperationsRequireBearerJwtAndDeclareUnauthorized() {
        for (Operation operation : ENTREGA1_OPERATIONS) {
            if (isPublicOrInternal(operation)) {
                continue;
            }
            Map<String, Object> op = operation(operation);
            @SuppressWarnings("unchecked")
            List<Object> security = (List<Object>) effectiveSecurity(op);
            assertThat(security)
                    .as("%s %s exige BearerJWT (global heredado o declarado)",
                            operation.method(), operation.path())
                    .containsExactly((Object) BEARER_REQUIREMENT);
            assertThat(responses(operation).keySet())
                    .as("%s %s declara 401 para token ausente, invalido o vencido",
                            operation.method(), operation.path())
                    .contains("401");
        }
    }

    @Test
    void publicQueriesAndInternalOperationsDeclareExplicitEmptySecurity() {
        for (Operation operation : ENTREGA1_OPERATIONS) {
            if (!isPublicOrInternal(operation)) {
                continue;
            }
            Object security = operation(operation).get("security");
            assertThat(security)
                    .as("%s %s declara security: [] explicito (sin Bearer)",
                            operation.method(), operation.path())
                    .isInstanceOf(List.class);
            assertThat((List<?>) security).isEmpty();
        }
    }

    @Test
    void noOperationDeclaresExternalIdentityHeaderParameters() {
        for (Operation operation : ENTREGA1_OPERATIONS) {
            for (Map<String, Object> parameter : resolvedParameters(operation)) {
                String name = String.valueOf(parameter.get("name"));
                assertThat(name)
                        .as("%s %s no declara el header externo X-User-Id",
                                operation.method(), operation.path())
                        .isNotEqualTo("X-User-Id");
                assertThat(name)
                        .as("%s %s no declara el header externo X-User-Role",
                                operation.method(), operation.path())
                        .isNotEqualTo("X-User-Role");
            }
        }
        Object registry = components().get("parameters");
        if (registry instanceof Map<?, ?> parameterRegistry) {
            @SuppressWarnings("unchecked")
            java.util.Set<Object> names = (java.util.Set<Object>) parameterRegistry.keySet();
            assertThat(names)
                    .as("components.parameters no define X-User-Id ni X-User-Role")
                    .doesNotContain("X-User-Id", "X-User-Role");
        }
    }

    @Test
    void documentDeclaresTheBearerJwtHttpSecuritySchemeWithGlobalRequirement() {
        Object securitySchemes = components().get("securitySchemes");
        assertThat(securitySchemes)
                .as("el documento declara components.securitySchemes")
                .isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> bearer = (Map<String, Object>)
                ((Map<String, Object>) securitySchemes).get("BearerJWT");
        assertThat(bearer).as("el esquema BearerJWT esta declarado").isNotNull();
        assertThat(bearer.get("type")).as("BearerJWT es de tipo http").isEqualTo("http");
        assertThat(bearer.get("scheme")).as("BearerJWT usa scheme bearer").isEqualTo("bearer");
        assertThat(bearer.get("bearerFormat")).as("BearerJWT declara JWT").isEqualTo("JWT");

        assertThat(contract.get("security"))
                .as("el documento exige BearerJWT globalmente para las operaciones protegidas")
                .isEqualTo(List.of(BEARER_REQUIREMENT));
    }

    @Test
    void updateEventDraftWarnsThatOfferingsAreIgnoredAndManagedViaOfferingsEndpoint() {
        Object description = operation(new Operation("/api/management/events/{id}", "put",
                "updateEventDraft", CATALOG)).get("description");
        assertThat(description)
                .as("PUT /api/management/events/{id} declara description")
                .isNotNull();
        String text = description.toString().toLowerCase();
        assertThat(text)
                .as("la description advierte que offerings se ignora en el update")
                .contains("offerings")
                .contains("ignora");
        assertThat(text)
                .as("la description redirige la edicion de ofertas a /offerings")
                .contains("/offerings");
    }

    @Test
    void everyPathIsEitherGatewayPublicApiOrInternalServiceRoute() {
        for (String path : paths().keySet()) {
            assertThat(path)
                    .as("%s es una ruta /api/** publica o /internal/** de servicios", path)
                    .matches("^/(api|internal)/.+");
        }
        // La superficie por el Gateway son solo rutas /api/**; las internas no pasan por el Gateway.
        assertThat(paths().keySet().stream().filter(path -> path.startsWith("/internal/")).count())
                .as("el contrato declara rutas internas")
                .isGreaterThan(0);
    }

    @Test
    void theContractDoesNotDeclareCapabilitiesOutsideTheImplementedScope() {
        for (String path : paths().keySet()) {
            String normalized = path.toLowerCase();
            for (String fragment : NOT_YET_IMPLEMENTED_CAPABILITY_FRAGMENTS) {
                assertThat(normalized)
                        .as("el path %s no declara una capacidad aún no implementada (%s)",
                                path, fragment)
                        .doesNotContain(fragment);
            }
        }
        for (String schemaName : schemas().keySet()) {
            String normalized = schemaName.toLowerCase();
            for (String fragment : NOT_YET_IMPLEMENTED_CAPABILITY_FRAGMENTS) {
                assertThat(normalized)
                        .as("el schema %s no declara una capacidad aún no implementada (%s)",
                                schemaName, fragment)
                        .doesNotContain(fragment);
            }
        }
    }

    @Test
    void contractDeclaresTheEntrega2OperationsWithStatusCodesAndSecurity() {
        for (Operation operation : ENTREGA2_OPERATIONS) {
            Map<String, Object> op = operation(operation);
            assertThat(op.get("operationId"))
                    .as("operationId de %s %s", operation.method(), operation.path())
                    .isEqualTo(operation.operationId());
            String key = operation.path() + ":" + operation.method();
            Set<Integer> documented = responses(operation).keySet().stream()
                    .filter(code -> code.matches("\\d{3}"))
                    .map(Integer::parseInt)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(documented).as("codigos minimos de %s", key).containsAll(ENTREGA2_STATUS_CODES.get(key));
            if (operation.path().startsWith("/internal/")) {
                assertThat(op.get("security")).as("%s es interna: security []", key).isEqualTo(List.of());
            } else {
                assertThat(op.get("security"))
                        .as("%s hereda el requisito BearerJWT global", key).isNull();
                @SuppressWarnings("unchecked")
                List<Object> security = (List<Object>) effectiveSecurity(op);
                assertThat(security).containsExactly((Object) BEARER_REQUIREMENT);
            }
        }
    }

    @Test
    void entrega2SchemasAreDeclared() {
        assertThat(schemas().keySet()).contains(
                "AccessValidationRequest", "AccessValidationResponse", "AccessProblem",
                "AccessScanPage", "AccessScanView",
                "OrganizerEventReport", "SectorReport", "ReportTotals",
                "AuditEventRequest", "AuditIngestResponse", "AuditEventView", "AuditEventPage", "AuditTrace");
        @SuppressWarnings("unchecked")
        Map<String, Object> scanView = (Map<String, Object>) schema("AccessScanView").get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) scanView.get("result");
        assertThat((List<String>) result.get("enum"))
                .containsExactlyInAnyOrder("ADMITTED", "ALREADY_USED", "REVOKED", "WRONG_EVENT", "UNKNOWN_TICKET");
    }

    @Test
    void contractDeclaresThePreferencesOperationsWithStatusCodesAndSecurity() {
        for (Operation operation : PREFERENCES_OPERATIONS) {
            Map<String, Object> op = operation(operation);
            assertThat(op.get("operationId"))
                    .as("operationId de %s %s", operation.method(), operation.path())
                    .isEqualTo(operation.operationId());
            String key = operation.path() + ":" + operation.method();
            Set<Integer> documented = responses(operation).keySet().stream()
                    .filter(code -> code.matches("\\d{3}"))
                    .map(Integer::parseInt)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(documented).as("codigos minimos de %s", key)
                    .containsAll(PREFERENCES_STATUS_CODES.get(key));
            if (operation.path().startsWith("/internal/")) {
                assertThat(op.get("security")).as("%s es interna: security []", key)
                        .isEqualTo(List.of());
            } else {
                assertThat(op.get("security"))
                        .as("%s hereda el requisito BearerJWT global", key).isNull();
                @SuppressWarnings("unchecked")
                List<Object> security = (List<Object>) effectiveSecurity(op);
                assertThat(security).containsExactly((Object) BEARER_REQUIREMENT);
            }
        }
    }

    @Test
    void contractDeclaresTheNotificationOperationsWithStatusCodesAndSecurity() {
        for (Operation operation : NOTIFICATION_OPERATIONS) {
            Map<String, Object> op = operation(operation);
            assertThat(op.get("operationId"))
                    .as("operationId de %s %s", operation.method(), operation.path())
                    .isEqualTo(operation.operationId());
            String key = operation.path() + ":" + operation.method();
            Set<Integer> documented = responses(operation).keySet().stream()
                    .filter(code -> code.matches("\\d{3}"))
                    .map(Integer::parseInt)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(documented).as("codigos minimos de %s", key)
                    .containsAll(NOTIFICATION_STATUS_CODES.get(key));
            if (operation.path().startsWith("/internal/")) {
                assertThat(op.get("security")).as("%s es interna: security []", key)
                        .isEqualTo(List.of());
            } else {
                assertThat(op.get("security"))
                        .as("%s hereda el requisito BearerJWT global", key).isNull();
                @SuppressWarnings("unchecked")
                List<Object> security = (List<Object>) effectiveSecurity(op);
                assertThat(security).containsExactly((Object) BEARER_REQUIREMENT);
            }
        }
    }

    @Test
    void notificationSchemasAreDeclared() {
        assertThat(schemas().keySet()).contains(
                "OrderNotificationRequest", "EventChangeNotificationRequest", "NotificationIngestResponse",
                "CustomerNotification", "CustomerNotificationPage", "EventBuyers", "PreferenceMatches",
                "CustomerEmailContactRequest", "CustomerEmailContact", "EventScheduleChangeRequest",
                "EventScheduleChangeResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema("CustomerNotification").get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> channel = (Map<String, Object>) properties.get("channel");
        assertThat((List<String>) channel.get("enum")).containsExactlyInAnyOrder("EMAIL", "PUSH");
    }

    @Test
    void preferencesSchemasAreDeclared() {
        assertThat(schemas().keySet()).contains(
                "CustomerPreferencesRequest", "CustomerPreferencesResponse",
                "RecommendedEvent", "EventRecommendationPage",
                "RecommendationSignals", "CoPurchaseSignal",
                "EventCandidate", "EventCandidatePage");
        assertThat(required("CustomerPreferencesResponse"))
                .contains("city", "eventTypes", "favoriteArtists", "updatedAt", "version");
        assertThat(required("RecommendedEvent"))
                .contains("eventId", "name", "eventType", "startsAt", "city",
                        "participants", "minPrice", "score", "reasons");
        assertThat(required("RecommendationSignals"))
                .contains("customerId", "purchasedEventIds", "coPurchases", "generatedAt");
        @SuppressWarnings("unchecked")
        Map<String, Object> request = schema("CustomerPreferencesRequest");
        assertThat(request.get("properties"))
                .as("CustomerPreferencesRequest declara properties")
                .isInstanceOf(Map.class);
    }

    @Test
    void eventResponseDeclaresTheExactEventStatusEnum() {
        @SuppressWarnings("unchecked")
        Map<String, Object> eventResponse = schema("EventResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) eventResponse.get("properties");
        assertThat(properties)
                .as("EventResponse declara properties")
                .isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) properties.get("status");
        assertThat(status)
                .as("EventResponse.properties.status declara el estado del evento")
                .isNotNull();
        assertThat(status.get("enum"))
                .as("EventResponse.properties.status.enum declara la lista de estados")
                .isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> statusEnum = (List<String>) status.get("enum");
        assertThat(statusEnum)
                .as("EventResponse.properties.status.enum declara exactamente los estados "
                        + "del ciclo de vida del evento en E1")
                .containsExactlyInAnyOrder(
                        "DRAFT", "PUBLISHED", "SUSPENDED", "SOLD_OUT", "CANCELLED",
                        "FINISHED");
    }

    @Test
    void entrega1SchemasOfEveryServiceRemainDeclared() {
        // Catalog
        assertThat(schemas().keySet()).contains(
                "VenueRequest", "VenueResponse", "VenuePageResponse",
                "EventRequest", "EventResponse", "EventSearchPage", "EventDetail",
                "OfferingsRequest", "OfferingResponse");
        // Inventory
        assertThat(schemas().keySet()).contains(
                "AvailabilityResponse", "SectorAvailability", "SeatAvailability",
                "ReservationRequest", "ReservationResponse",
                "SoldUnitReleaseRequest", "SoldUnitReleaseResponse",
                "EventHoldConfiguration", "CatalogEventSnapshot", "CatalogPurchaseSnapshot");
        // Purchase
        assertThat(schemas().keySet()).contains(
                "CreateOrderRequest", "OrderResponse", "OrderPage",
                "RefundView", "CancellationProgress");
        // Ticket
        assertThat(schemas().keySet()).contains(
                "TicketIssueRequest", "TicketIssueResponse", "TicketRevocationResponse");
        // Payment Mock
        assertThat(schemas().keySet()).contains(
                "CreatePaymentRequest", "PaymentResponse",
                "PartialRefundRequest", "PartialRefundResponse");
        // Errores
        assertThat(schemas().keySet()).contains("Problem");
    }
}
