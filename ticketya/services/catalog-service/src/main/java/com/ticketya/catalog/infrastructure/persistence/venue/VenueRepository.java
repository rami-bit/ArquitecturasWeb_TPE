package com.ticketya.catalog.infrastructure.persistence.venue;

import com.ticketya.catalog.domain.venue.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
