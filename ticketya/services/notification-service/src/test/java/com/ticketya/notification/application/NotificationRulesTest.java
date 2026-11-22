package com.ticketya.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas puras de F11: espera de reintentos, textos y tipos aceptados. */
class NotificationRulesTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void retryDelayGrowsExponentiallyUpToTheMaximum() {
        assertThat(RetryPolicy.delayMs(0, 2000, 60000)).isZero();
        assertThat(RetryPolicy.delayMs(1, 2000, 60000)).isEqualTo(2000);
        assertThat(RetryPolicy.delayMs(2, 2000, 60000)).isEqualTo(4000);
        assertThat(RetryPolicy.delayMs(4, 2000, 60000)).isEqualTo(16000);
        assertThat(RetryPolicy.delayMs(10, 2000, 60000)).isEqualTo(60000);
        assertThat(RetryPolicy.delayMs(3, 0, 60000)).isZero();
    }

    @Test
    void messagesNameTheEventAndNeverExposePaymentData() {
        ObjectNode payload = json.createObjectNode().put("eventName", "Festival").put("orderId", "o-1")
                .put("startsAt", "2027-01-01T20:00:00Z").put("city", "Córdoba").put("token", "tok_approved");
        payload.putArray("participants").add("Banda A").add(" ").add("Banda B");

        assertThat(MessageTemplates.render(TriggerType.ORDER_CONFIRMED, payload).subject())
                .isEqualTo("Compra confirmada: Festival");
        assertThat(MessageTemplates.render(TriggerType.EVENT_PUBLISHED, payload).body())
                .isEqualTo("Festival con Banda A, Banda B (2027-01-01T20:00:00Z). Las entradas ya están a la venta.");
        assertThat(MessageTemplates.render(TriggerType.EVENT_SUSPENDED, payload).subject())
                .isEqualTo("Evento suspendido: Festival");
        assertThat(MessageTemplates.render(TriggerType.EVENT_RESUMED, payload).body()).contains("vuelve a estar");
        for (TriggerType type : TriggerType.values()) {
            MessageTemplates.Message message = MessageTemplates.render(type, payload);
            assertThat(message.subject()).isNotBlank().hasSizeLessThanOrEqualTo(200);
            assertThat(message.body()).isNotBlank().doesNotContain("tok_");
        }
        assertThat(MessageTemplates.render(TriggerType.EVENT_CANCELLED, json.createObjectNode()).subject())
                .isEqualTo("Evento cancelado: tu evento");
    }

    @Test
    void eachEndpointAcceptsOnlyItsTypes() {
        assertThat(TriggerType.parse("ORDER_CONFIRMED", TriggerType.ORDER_TYPES)).isEqualTo(TriggerType.ORDER_CONFIRMED);
        assertThat(TriggerType.parse("EVENT_CANCELLED", TriggerType.ORDER_TYPES)).isNull();
        assertThat(TriggerType.parse("EVENT_PUBLISHED", TriggerType.EVENT_TYPES)).isEqualTo(TriggerType.EVENT_PUBLISHED);
        assertThat(TriggerType.parse("bogus", TriggerType.EVENT_TYPES)).isNull();
        assertThat(TriggerType.EVENT_SUSPENDED.repeatable()).isTrue();
        assertThat(TriggerType.EVENT_CANCELLED.repeatable()).isFalse();
    }
}
