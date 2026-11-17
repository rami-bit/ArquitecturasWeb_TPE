package com.ticketya.purchase.contract;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de admisión de la fila virtual en {@code POST /api/orders}.
 *
 * <p>El snapshot de {@link PurchaseContractTestBase#HIGH_DEMAND_EVENT} informa
 * {@code highDemand: true}; los eventos comunes no lo hacen y no deben cambiar de
 * comportamiento. El token se consume en el intento de compra y no se persiste
 * en Purchase.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseAdmissionContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    @Test
    void highDemandOrderWithoutAdmissionTokenReturns403BeforeInventory() {
        UUID eventId = HIGH_DEMAND_EVENT;
        String body = highDemandBody(eventId);

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-admission-missing"));

        assertProblem(response, HttpStatus.FORBIDDEN);
        assertThat(CATALOG_REQUESTS).hasSize(1);
        assertThat(INVENTORY_REQUESTS).as("sin token no se consulta inventario").isEmpty();
        assertThat(PAYMENT_REQUESTS).as("sin token no se cobra").isEmpty();
        assertThat(TICKET_REQUESTS).as("sin token no se emiten tickets").isEmpty();
        assertThat(validateAdmissionCallCount()).isZero();
    }

    @Test
    void highDemandOrderWithValidTokenBuysAndDoesNotExposeTheToken() {
        UUID eventId = HIGH_DEMAND_EVENT;
        Map<String, String> headers = buyerHeaders(buyer, "key-admission-ok");
        headers.put(ADMISSION_HEADER, "token-adm-ok");

        ResponseEntity<String> response = createOrder(highDemandBody(eventId), headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(response).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(response.getBody())
                .as("el token de admisión no se persiste ni se devuelve")
                .doesNotContain("token-adm-ok");

        assertThat(validateAdmissionCallCount()).isEqualTo(1);
        RecordedRequest validation = validateAdmissionCalls().get(0);
        assertThat(validation.body())
                .contains(eventId.toString())
                .contains(buyer.toString())
                .contains("token-adm-ok")
                .contains("key-admission-ok");
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countTicketIssuances()).isEqualTo(1);
    }

    @Test
    void highDemandOrderWithRejectedTokenReturns403WithoutInventoryOrPayment() {
        UUID eventId = HIGH_DEMAND_EVENT;
        Map<String, String> headers = buyerHeaders(buyer, "key-admission-rejected");
        headers.put(ADMISSION_HEADER, "token-ajeno-o-vencido");
        failQueueAdmission(403);

        ResponseEntity<String> response = createOrder(highDemandBody(eventId), headers);

        assertProblem(response, HttpStatus.FORBIDDEN);
        assertThat(validateAdmissionCallCount()).isEqualTo(1);
        assertThat(INVENTORY_REQUESTS).as("una admisión rechazada no reserva").isEmpty();
        assertThat(PAYMENT_REQUESTS).as("una admisión rechazada no cobra").isEmpty();
        assertThat(TICKET_REQUESTS).as("una admisión rechazada no emite tickets").isEmpty();
    }

    @Test
    void highDemandOrderWithUnknownEntryReturns403() {
        UUID eventId = HIGH_DEMAND_EVENT;
        Map<String, String> headers = buyerHeaders(buyer, "key-admission-404");
        headers.put(ADMISSION_HEADER, "token-sin-turno");
        failQueueAdmission(404);

        ResponseEntity<String> response = createOrder(highDemandBody(eventId), headers);

        assertProblem(response, HttpStatus.FORBIDDEN);
        assertThat(INVENTORY_REQUESTS).isEmpty();
    }

    @Test
    void highDemandOrderWithClosedQueueReturns409() {
        UUID eventId = HIGH_DEMAND_EVENT;
        Map<String, String> headers = buyerHeaders(buyer, "key-admission-closed");
        headers.put(ADMISSION_HEADER, "token-closed");
        failQueueAdmission(409);

        ResponseEntity<String> response = createOrder(highDemandBody(eventId), headers);

        assertProblem(response, HttpStatus.CONFLICT);
        assertThat(INVENTORY_REQUESTS).isEmpty();
        assertThat(PAYMENT_REQUESTS).isEmpty();
    }

    @Test
    void highDemandOrderWithUnavailableQueueReturns503() {
        UUID eventId = HIGH_DEMAND_EVENT;
        Map<String, String> headers = buyerHeaders(buyer, "key-admission-503");
        headers.put(ADMISSION_HEADER, "token-unavailable");
        failQueueAdmission(503);

        ResponseEntity<String> response = createOrder(highDemandBody(eventId), headers);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(INVENTORY_REQUESTS).isEmpty();
        assertThat(PAYMENT_REQUESTS).isEmpty();
    }

    @Test
    void lowDemandOrderDoesNotRequireNorConsumeAdmission() {
        UUID eventId = UUID.randomUUID();
        String body = orderBody(eventId,
                quantityItem(derive(eventId, "inventory:sector:campo"), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-low-demand-" + eventId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(response).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(validateAdmissionCallCount())
                .as("un evento sin alta demanda no consulta la fila")
                .isZero();
    }

    @Test
    void replayWithSameIdempotencyKeyDoesNotConsumeAdmissionAgain() {
        UUID eventId = HIGH_DEMAND_EVENT;
        String body = highDemandBody(eventId);
        Map<String, String> headers = buyerHeaders(buyer, "key-admission-replay");
        headers.put(ADMISSION_HEADER, "token-admission-replay");

        ResponseEntity<String> created = createOrder(body, headers);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = parseBody(created).path("orderId").asText();
        assertThat(validateAdmissionCallCount()).isEqualTo(1);

        ResponseEntity<String> replay = createOrder(body, headers);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(replay).path("orderId").asText()).isEqualTo(orderId);
        assertThat(validateAdmissionCallCount())
                .as("el replay idempotente no consume un segundo token")
                .isEqualTo(1);
    }

    @Test
    void reusedTokenWithDifferentIdempotencyKeyIsRejectedByTheQueue() {
        UUID eventId = HIGH_DEMAND_EVENT;
        String body = highDemandBody(eventId);
        Map<String, String> firstHeaders = buyerHeaders(buyer, "key-admission-first");
        firstHeaders.put(ADMISSION_HEADER, "token-reutilizado");

        assertThat(createOrder(body, firstHeaders).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long reservations = countPosts(INVENTORY_REQUESTS, "/internal/reservations");

        Map<String, String> secondHeaders = buyerHeaders(buyer, "key-admission-second");
        secondHeaders.put(ADMISSION_HEADER, "token-reutilizado");
        failQueueAdmission(403);

        ResponseEntity<String> reused = createOrder(body, secondHeaders);

        assertProblem(reused, HttpStatus.FORBIDDEN);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations"))
                .as("el segundo intento no vuelve a reservar")
                .isEqualTo(reservations);
    }

    private static String highDemandBody(UUID eventId) {
        return orderBody(eventId,
                quantityItem(derive(eventId, "inventory:sector:campo"), 1),
                "CARD", "tok_approved");
    }
}
