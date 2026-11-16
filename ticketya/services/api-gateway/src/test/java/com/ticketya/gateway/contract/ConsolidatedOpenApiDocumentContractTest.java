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
 * capacidades de Entrega 2 (JWT, cola virtual, control de acceso, preferencias,
 * recomendaciones, notificaciones y reportes) NO esten declaradas, verifica las
 * reglas transversales de {@code application/problem+json} y {@code X-Correlation-Id},
 * y protege las reglas de autorizacion de Catalog: {@code X-User-Role} required con
 * enum exacto (ADMIN para crear/editar venues; ORGANIZER/ADMIN para listar venues y
 * gestionar eventos con {@code X-User-Id} obligatorio; ADMIN para
 * suspend/cancel/resume) y la advertencia de que el update de evento ignora
 * {@code offerings} y debe usarse {@code /offerings}.
 */
class ConsolidatedOpenApiDocumentContractTest {

    private record Operation(String path, String method, String operationId, String owner) {
    }

    /**
     * Regla de autorizacion declarada en el contrato: roles exactos de
     * {@code X-User-Role} y, para la gestion de eventos, {@code X-User-Id}
     * obligatorio.
     */
    private record RoleRule(String path, String method, List<String> roles, boolean requiresUserId) {
    }

    private static final String CATALOG = "Catalog";
    private static final String INVENTORY = "Inventory";
    private static final String PURCHASE = "Purchase";
    private static final String TICKET = "Ticket";
    private static final String PAYMENT = "Payment";

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

    /** Capacidades planificadas para Entrega 2; no deben aparecer en el contrato E1. */
    private static final List<String> ENTREGA2_CAPABILITY_FRAGMENTS = List.of(
            "jwt", "auth", "login", "token", "queue", "preference", "recommendation",
            "notification", "report", "access-use", "accessuse");

    /** Reglas de roles exigidas por Catalog: venues y gestion de eventos. */
    private static final List<RoleRule> ROLE_RULES = List.of(
            // Venues: creacion/edicion solo ADMIN; listado ORGANIZER o ADMIN.
            new RoleRule("/api/venues", "post", List.of("ADMIN"), false),
            new RoleRule("/api/venues/{id}", "put", List.of("ADMIN"), false),
            new RoleRule("/api/venues", "get", List.of("ORGANIZER", "ADMIN"), false),
            // Gestion de eventos: ORGANIZER o ADMIN con X-User-Id obligatorio.
            new RoleRule("/api/management/events", "post", List.of("ORGANIZER", "ADMIN"), true),
            new RoleRule("/api/management/events", "get", List.of("ORGANIZER", "ADMIN"), true),
            new RoleRule("/api/management/events/{id}", "get", List.of("ORGANIZER", "ADMIN"), true),
            new RoleRule("/api/management/events/{id}", "put", List.of("ORGANIZER", "ADMIN"), true),
            new RoleRule("/api/management/events/{id}/offerings", "put",
                    List.of("ORGANIZER", "ADMIN"), true),
            new RoleRule("/api/management/events/{id}/publish", "post",
                    List.of("ORGANIZER", "ADMIN"), true),
            // Comandos de control de estado: solo ADMIN.
            new RoleRule("/api/management/events/{id}/suspend", "post", List.of("ADMIN"), true),
            new RoleRule("/api/management/events/{id}/cancel", "post", List.of("ADMIN"), true),
            new RoleRule("/api/management/events/{id}/resume", "post", List.of("ADMIN"), true));

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

    /** Header (name, in=header) resuelto de la operacion, o null si no esta declarado. */
    private static Map<String, Object> resolvedHeaderParameter(Operation operation,
            String headerName) {
        for (Map<String, Object> parameter : resolvedParameters(operation)) {
            if (headerName.equals(parameter.get("name"))
                    && "header".equals(parameter.get("in"))) {
                return parameter;
            }
        }
        return null;
    }

    /** Enum exacto del schema del parametro, resolviendo $ref a components.schemas. */
    @SuppressWarnings("unchecked")
    private static List<String> schemaEnum(Map<String, Object> parameter) {
        Object schema = parameter.get("schema");
        assertThat(schema)
                .as("el parametro %s declara schema", parameter.get("name"))
                .isNotNull();
        if (schema instanceof Map<?, ?> refSchema
                && refSchema.get("$ref") instanceof String reference) {
            assertThat(reference)
                    .as("las refs de schemas apuntan a components.schemas")
                    .startsWith("#/components/schemas/");
            String name = reference.substring("#/components/schemas/".length());
            schema = schema(name);
        }
        Object enums = ((Map<String, Object>) schema).get("enum");
        assertThat(enums)
                .as("el schema de %s declara enum", parameter.get("name"))
                .isInstanceOf(List.class);
        return (List<String>) enums;
    }

    /** Operation de Entrega 1 correspondiente a un par path:metodo de las reglas. */
    private static Operation operationOf(RoleRule rule) {
        return ENTREGA1_OPERATIONS.stream()
                .filter(candidate -> candidate.path().equals(rule.path())
                        && candidate.method().equals(rule.method()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "regla de rol sin operacion: " + rule.path() + ":" + rule.method()));
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
    void venueOperationsDeclareRequiredUserRoleWithExactEnums() {
        for (RoleRule rule : ROLE_RULES.stream()
                .filter(candidate -> candidate.path().startsWith("/api/venues"))
                .toList()) {
            Map<String, Object> roleParameter = resolvedHeaderParameter(
                    operationOf(rule), "X-User-Role");
            assertThat(roleParameter)
                    .as("%s %s declara el header X-User-Role (inline o ref)",
                            rule.method(), rule.path())
                    .isNotNull();
            assertThat(roleParameter.get("required"))
                    .as("X-User-Role es required=true en %s %s", rule.method(), rule.path())
                    .isEqualTo(Boolean.TRUE);
            assertThat(schemaEnum(roleParameter))
                    .as("enum exacto de X-User-Role en %s %s", rule.method(), rule.path())
                    .containsExactlyInAnyOrderElementsOf(rule.roles());
        }
    }

    @Test
    void eventManagementOperationsDeclareRequiredUserIdAndExactRoleEnums() {
        for (RoleRule rule : ROLE_RULES.stream()
                .filter(candidate -> candidate.path().startsWith("/api/management/events"))
                .toList()) {
            Map<String, Object> roleParameter = resolvedHeaderParameter(
                    operationOf(rule), "X-User-Role");
            assertThat(roleParameter)
                    .as("%s %s declara el header X-User-Role (inline o ref)",
                            rule.method(), rule.path())
                    .isNotNull();
            assertThat(roleParameter.get("required"))
                    .as("X-User-Role es required=true en %s %s", rule.method(), rule.path())
                    .isEqualTo(Boolean.TRUE);
            assertThat(schemaEnum(roleParameter))
                    .as("enum exacto de X-User-Role en %s %s", rule.method(), rule.path())
                    .containsExactlyInAnyOrderElementsOf(rule.roles());

            Map<String, Object> userParameter = resolvedHeaderParameter(
                    operationOf(rule), "X-User-Id");
            assertThat(userParameter)
                    .as("%s %s declara el header X-User-Id (inline o ref)",
                            rule.method(), rule.path())
                    .isNotNull();
            assertThat(userParameter.get("required"))
                    .as("X-User-Id es required=true en %s %s", rule.method(), rule.path())
                    .isEqualTo(Boolean.TRUE);
        }
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
    void theDocumentDeclaresNoAuthenticationSurfaceForEntrega1() {
        assertThat(components())
                .as("E1 no declara securitySchemes (sin JWT)")
                .doesNotContainKey("securitySchemes");

        Object documentSecurity = contract.get("security");
        if (documentSecurity != null) {
            assertThat((List<?>) documentSecurity)
                    .as("security global vacio en E1")
                    .isEmpty();
        }

        for (Map.Entry<String, Map<String, Object>> entry
                : allDocumentedOperations().entrySet()) {
            Object security = entry.getValue().get("security");
            if (security != null) {
                assertThat((List<Object>) security)
                        .as("%s no declara requisitos de seguridad", entry.getKey())
                        .isEmpty();
            }
        }
    }

    @Test
    void theContractDoesNotDeclareEntrega2Capabilities() {
        for (String path : paths().keySet()) {
            String normalized = path.toLowerCase();
            for (String fragment : ENTREGA2_CAPABILITY_FRAGMENTS) {
                assertThat(normalized)
                        .as("el path %s no declara capacidad de Entrega 2 (%s)",
                                path, fragment)
                        .doesNotContain(fragment);
            }
        }
        for (String schemaName : schemas().keySet()) {
            String normalized = schemaName.toLowerCase();
            for (String fragment : ENTREGA2_CAPABILITY_FRAGMENTS) {
                assertThat(normalized)
                        .as("el schema %s no declara capacidad de Entrega 2 (%s)",
                                schemaName, fragment)
                        .doesNotContain(fragment);
            }
        }
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
