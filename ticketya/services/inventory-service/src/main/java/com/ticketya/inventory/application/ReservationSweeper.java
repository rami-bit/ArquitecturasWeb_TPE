package com.ticketya.inventory.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationSweeper {
    private final ReservationExpiryService expiry;

    public ReservationSweeper(ReservationExpiryService expiry) {
        this.expiry = expiry;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void sweep() {
        expiry.expireDue();
    }
}
