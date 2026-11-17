package com.ticketya.audit.application;

import com.ticketya.audit.api.error.ApiProblemException;
import com.ticketya.audit.api.events.AuditEventPage;
import com.ticketya.audit.api.events.AuditEventRequest;
import com.ticketya.audit.api.events.AuditEventView;
import com.ticketya.audit.api.events.AuditTrace;
import com.ticketya.audit.domain.AuditSearchCriteria;
import com.ticketya.audit.infrastructure.JdbcAuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AuditEventService {
    private final JdbcAuditEventRepository repository;

    public AuditEventService(JdbcAuditEventRepository repository) {
        this.repository = repository;
    }

    /** Registra el evento; un reintento con el mismo eventId no duplica el registro. */
    public boolean record(AuditEventRequest event) {
        if (event.details() != null && !event.details().isNull() && !event.details().isObject()) {
            throw ApiProblemException.badRequest("details must be a JSON object");
        }
        return repository.insertIfAbsent(event);
    }

    public AuditEventPage search(AuditSearchCriteria criteria) {
        List<AuditEventView> items = repository.search(criteria);
        return new AuditEventPage(items, criteria.page(), criteria.size(), repository.count(criteria));
    }

    public AuditTrace trace(String correlationId) {
        List<AuditEventView> events = repository.findByCorrelationId(correlationId);
        if (events.isEmpty()) {
            throw ApiProblemException.notFound("No audit events for correlationId " + correlationId);
        }
        LinkedHashSet<String> services = new LinkedHashSet<>();
        events.forEach(event -> services.add(event.service()));
        return new AuditTrace(correlationId, events.size(), List.copyOf(services),
                events.get(0).occurredAt(), events.get(events.size() - 1).occurredAt(), events);
    }
}
