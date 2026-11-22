package com.ticketya.catalog.infrastructure.persistence.event;
import com.ticketya.catalog.domain.event.Event; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface EventRepository extends JpaRepository<Event,UUID>{
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"participants"}) List<Event> findAllByOrderByNameAscIdAsc();
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"participants"}) List<Event> findAllByOrganizerIdOrderByNameAscIdAsc(UUID organizerId);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"participants"}) Optional<Event> findById(UUID id);
 Optional<Event> findByIdAndOrganizerId(UUID id,UUID organizerId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select e from Event e where e.id = :id")
 Optional<Event> findForScheduleUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
