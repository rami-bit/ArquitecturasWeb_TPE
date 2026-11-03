package com.ticketya.catalog.infrastructure.persistence.venue;
import com.ticketya.catalog.domain.venue.VenueSector; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface VenueSectorRepository extends JpaRepository<VenueSector,UUID>{ List<VenueSector> findAllByVenueId(UUID venueId); }
