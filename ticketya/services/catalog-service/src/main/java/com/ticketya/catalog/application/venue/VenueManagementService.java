package com.ticketya.catalog.application.venue;

import com.ticketya.catalog.api.error.ApiProblemException;
import com.ticketya.catalog.domain.venue.SectorDefinition;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.venue.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@Transactional
public class VenueManagementService {
    private final VenueRepository repository;

    public VenueManagementService(VenueRepository repository) { this.repository = repository; }

    public Venue create(String role, String name, String address, String city, BigDecimal latitude,
                        BigDecimal longitude, List<SectorDefinition> sectors) {
        requireAdmin(role);
        Venue venue = new Venue(name, address, city, latitude, longitude);
        if (sectors != null) venue.replaceSectors(sectors);
        return repository.save(venue);
    }

    @Transactional(readOnly = true)
    public Page<Venue> list(String role, int page, int size) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", "ORGANIZER or ADMIN role is required");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid pagination", "Page must be non-negative and size must be between 1 and 100");
        }
        return repository.findAll(PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id").ascending())));
    }

    public Venue update(String role, UUID id, String name, String address, String city, BigDecimal latitude,
                        BigDecimal longitude, List<SectorDefinition> sectors) {
        requireAdmin(role);
        Venue venue = repository.findById(id).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "Venue not found", "No venue exists with id " + id));
        venue.updateDetails(name, address, city, latitude, longitude);
        if (sectors != null) venue.replaceSectors(sectors);
        return venue;
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) throw new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", "ADMIN role is required");
    }
}
