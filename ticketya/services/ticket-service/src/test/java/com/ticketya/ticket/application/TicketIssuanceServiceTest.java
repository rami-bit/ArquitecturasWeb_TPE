package com.ticketya.ticket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ticket.api.error.ApiProblemException;
import com.ticketya.ticket.api.issuance.TicketIssueRequest;
import com.ticketya.ticket.api.issuance.TicketUnitRequest;
import com.ticketya.ticket.domain.Ticket;
import com.ticketya.ticket.domain.TicketIssuance;
import com.ticketya.ticket.infrastructure.persistence.TicketIssuanceRepository;
import com.ticketya.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketIssuanceServiceTest {
    @Mock
    private TicketIssuanceRepository issuanceRepository;

    @Mock
    private TicketRepository ticketRepository;

    private TicketIssuanceService service;

    @BeforeEach
    void setUp() {
        service = new TicketIssuanceService(
                issuanceRepository,
                ticketRepository,
                new TicketQrSigner("a-unit-test-signing-secret-with-at-least-32-bytes"),
                new QrCodeRenderer(),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createsOneTicketPerUnitWithStableDisplaySnapshot() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        TicketIssueRequest request = request(
                new TicketUnitRequest(2, "A", 2),
                new TicketUnitRequest(1, "A", 1));
        AtomicReference<TicketIssuance> issuanceRef = new AtomicReference<>();
        List<Ticket> persistedTickets = new ArrayList<>();

        when(issuanceRepository.insertIfAbsent(any(), any(), any(), anyString(), any()))
                .thenAnswer(call -> {
                    TicketIssuance issuance = new TicketIssuance(
                            call.getArgument(0),
                            call.getArgument(1),
                            call.getArgument(2),
                            call.getArgument(3),
                            call.getArgument(4));
                    issuanceRef.set(issuance);
                    return 1;
                });
        when(issuanceRepository.findByOrderIdAndOrderItemId(orderId, orderItemId))
                .thenAnswer(call -> Optional.ofNullable(issuanceRef.get()));
        when(ticketRepository.saveAllAndFlush(any()))
                .thenAnswer(call -> {
                    List<Ticket> tickets = call.getArgument(0);
                    persistedTickets.addAll(tickets);
                    return tickets;
                });
        when(ticketRepository.findByIssuanceIdOrderByUnitNumberAsc(any()))
                .thenAnswer(call -> persistedTickets.stream()
                        .sorted(java.util.Comparator.comparingInt(Ticket::getUnitNumber))
                        .toList());

        TicketIssuanceService.IssueResult result =
                service.issue(orderId, orderItemId, request);

        assertThat(result.created()).isTrue();
        assertThat(result.response().tickets()).hasSize(2);
        assertThat(result.response().tickets())
                .extracting("unitNumber").containsExactly(1, 2);
        assertThat(result.response().tickets())
                .allSatisfy(ticket -> {
                    assertThat(ticket.eventName()).isEqualTo("Concert");
                    assertThat(ticket.sectorName()).isEqualTo("Plate");
                    assertThat(ticket.qrPayload()).startsWith("v1.");
                    assertThat(ticket.qrPngBase64()).isNotBlank();
                });
    }

    @Test
    void identicalRetryReturnsPersistedTicketsWithoutSavingAgain() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        TicketIssueRequest request = request(new TicketUnitRequest(1, null, null));
        AtomicReference<TicketIssuance> issuanceRef = new AtomicReference<>();
        List<Ticket> persistedTickets = new ArrayList<>();
        AtomicInteger insertCall = new AtomicInteger();

        when(issuanceRepository.insertIfAbsent(any(), any(), any(), anyString(), any()))
                .thenAnswer(call -> {
                    if (insertCall.getAndIncrement() > 0) {
                        return 0;
                    }
                    issuanceRef.set(new TicketIssuance(
                            call.getArgument(0),
                            call.getArgument(1),
                            call.getArgument(2),
                            call.getArgument(3),
                            call.getArgument(4)));
                    return 1;
                });
        when(issuanceRepository.findByOrderIdAndOrderItemId(orderId, orderItemId))
                .thenAnswer(call -> Optional.ofNullable(issuanceRef.get()));
        when(ticketRepository.saveAllAndFlush(any()))
                .thenAnswer(call -> {
                    List<Ticket> tickets = call.getArgument(0);
                    persistedTickets.addAll(tickets);
                    return tickets;
                });
        when(ticketRepository.findByIssuanceIdOrderByUnitNumberAsc(any()))
                .thenAnswer(call -> List.copyOf(persistedTickets));

        TicketIssuanceService.IssueResult created =
                service.issue(orderId, orderItemId, request);
        TicketIssuanceService.IssueResult repeated =
                service.issue(orderId, orderItemId, request);

        assertThat(created.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.response().tickets().get(0).ticketId())
                .isEqualTo(created.response().tickets().get(0).ticketId());
        assertThat(repeated.response().tickets().get(0).qrPayload())
                .isEqualTo(created.response().tickets().get(0).qrPayload());
        verify(ticketRepository).saveAllAndFlush(any());
    }

    @Test
    void reusedOrderItemWithDifferentContentConflicts() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        TicketIssueRequest first = request(new TicketUnitRequest(1, "B", 8));
        TicketIssueRequest different = request(new TicketUnitRequest(1, "B", 9));
        AtomicReference<TicketIssuance> issuanceRef = new AtomicReference<>();
        AtomicInteger insertCall = new AtomicInteger();

        when(issuanceRepository.insertIfAbsent(any(), any(), any(), anyString(), any()))
                .thenAnswer(call -> {
                    if (insertCall.getAndIncrement() > 0) {
                        return 0;
                    }
                    issuanceRef.set(new TicketIssuance(
                            call.getArgument(0),
                            call.getArgument(1),
                            call.getArgument(2),
                            call.getArgument(3),
                            call.getArgument(4)));
                    return 1;
                });
        when(issuanceRepository.findByOrderIdAndOrderItemId(orderId, orderItemId))
                .thenAnswer(call -> Optional.ofNullable(issuanceRef.get()));
        when(ticketRepository.saveAllAndFlush(any()))
                .thenAnswer(call -> call.getArgument(0));
        when(ticketRepository.findByIssuanceIdOrderByUnitNumberAsc(any()))
                .thenAnswer(call -> List.of(new Ticket(
                        UUID.randomUUID(),
                        issuanceRef.get().getId(),
                        1,
                        first.customerId(),
                        first.eventId(),
                        first.eventName(),
                        first.eventStartsAt(),
                        first.venueName(),
                        first.venueCity(),
                        first.sectorName(),
                        "B",
                        8,
                        "stored-hash",
                        OffsetDateTime.now())));

        service.issue(orderId, orderItemId, first);

        assertThatThrownBy(() -> service.issue(orderId, orderItemId, different))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("different ticket details");
    }

    @Test
    void rejectsIncompleteSeatBeforeWritingAnything() {
        TicketIssueRequest request = request(new TicketUnitRequest(1, "A", null));

        assertThatThrownBy(() -> service.issue(
                UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("both rowLabel and seatNumber");
        verify(issuanceRepository, never())
                .insertIfAbsent(any(), any(), any(), anyString(), any());
    }

    private TicketIssueRequest request(TicketUnitRequest... units) {
        return new TicketIssueRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Concert",
                OffsetDateTime.parse("2030-07-14T20:00:00-03:00"),
                "Arena",
                "Buenos Aires",
                "Plate",
                List.of(units));
    }
}
