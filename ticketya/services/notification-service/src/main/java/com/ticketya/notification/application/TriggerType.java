package com.ticketya.notification.application;

import java.util.EnumSet;
import java.util.Set;

/** Hechos de negocio que generan notificaciones (PDF-F11). */
public enum TriggerType {
    /** Purchase: compra confirmada. Destinatario: el comprador. */
    ORDER_CONFIRMED,
    /** Purchase: orden compensada (pago reintegrado). Destinatario: el comprador. */
    ORDER_CANCELLED,
    /** Catalog: evento publicado. Destinatarios: clientes con un artista favorito del evento en su ciudad. */
    EVENT_PUBLISHED,
    /** Catalog: evento suspendido (cambio). Destinatarios: compradores. */
    EVENT_SUSPENDED,
    /** Catalog: evento reanudado (cambio). Destinatarios: compradores. */
    EVENT_RESUMED,
    /** Catalog: evento cancelado. Destinatarios: compradores. */
    EVENT_CANCELLED,
    EVENT_POSTPONED;

    public static final Set<TriggerType> ORDER_TYPES = EnumSet.of(ORDER_CONFIRMED, ORDER_CANCELLED);
    public static final Set<TriggerType> EVENT_TYPES =
            EnumSet.of(EVENT_PUBLISHED, EVENT_SUSPENDED, EVENT_RESUMED, EVENT_CANCELLED, EVENT_POSTPONED);

    /** Los cambios repetibles (suspender/reanudar) se deduplican por cambio y no por evento. */
    public boolean repeatable() {
        return this == EVENT_SUSPENDED || this == EVENT_RESUMED || this == EVENT_POSTPONED;
    }

    public static TriggerType parse(String value, Set<TriggerType> allowed) {
        if (value == null) {
            return null;
        }
        try {
            TriggerType type = valueOf(value.trim());
            return allowed.contains(type) ? type : null;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
