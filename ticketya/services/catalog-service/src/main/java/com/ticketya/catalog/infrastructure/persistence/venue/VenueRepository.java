package com.ticketya.catalog.infrastructure.persistence.venue;

import com.ticketya.catalog.domain.venue.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
    Page<Venue> findAllByOrderByNameAscIdAsc(Pageable pageable);
}
