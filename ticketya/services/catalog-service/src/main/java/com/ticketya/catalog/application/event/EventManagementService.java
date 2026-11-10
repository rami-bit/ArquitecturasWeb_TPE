package com.ticketya.catalog.application.event;
import com.ticketya.catalog.api.error.ApiProblemException; import com.ticketya.catalog.domain.event.*; import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.event.EventRepository; import com.ticketya.catalog.infrastructure.persistence.venue.*;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.util.*;
@Service @Transactional
public class EventManagementService {
 private final EventRepository events; private final VenueRepository venues; private final VenueSectorRepository sectors;
private final InventoryCancellationClient inventory;
public EventManagementService(EventRepository e,VenueRepository v,VenueSectorRepository s,InventoryCancellationClient i){events=e;venues=v;sectors=s;inventory=i;}
 public Event create(String user,String role,String name,String description,String type,java.time.OffsetDateTime startsAt,UUID organizer,UUID venueId,List<String> participants,Map<UUID,BigDecimal> offerings){
  UUID actor=identity(user,role); UUID owner;
  if("ADMIN".equals(role)){if(organizer==null)throw bad("ADMIN must provide organizerId");owner=organizer;}else if("ORGANIZER".equals(role)){owner=actor;if(organizer!=null&&!organizer.equals(actor))throw forbidden();}else throw forbidden();
  Venue v=venue(venueId); Event e=new Event(name,description,type,startsAt,owner,v); if(participants!=null)e.replaceParticipants(participants); if(offerings!=null){validateOfferings(v,offerings);e.replaceOfferings(offerings);} return events.save(e);
 }
 public Event update(String user,String role,UUID id,String name,String description,String type,java.time.OffsetDateTime startsAt,UUID venueId,List<String> participants){
  Event e=authorized(user,role,id); draft(e); Venue v=venue(venueId); e.update(name,description,type,startsAt);if(!e.getVenue().getId().equals(v.getId()))e.clearOfferings();e.setVenue(v);if(participants!=null)e.replaceParticipants(participants);return e;
 }
 public Event offerings(String user,String role,UUID id,Map<UUID,BigDecimal> prices){Event e=authorized(user,role,id);draft(e);validateOfferings(e.getVenue(),prices);e.replaceOfferings(prices);return e;}
 public Event publish(String user,String role,UUID id){
  Event e=authorized(user,role,id);
  if(e.getStatus()==EventStatus.PUBLISHED && e.getSnapshot()!=null)return e;
  draft(e);
  if(e.getName()==null||e.getDescription()==null||e.getEventType()==null||e.getVenue()==null)throw bad("Event data is incomplete");
  if(!e.getStartsAt().isAfter(java.time.OffsetDateTime.now()))throw bad("Event start time must be in the future");
  if(e.getParticipants().isEmpty())throw bad("At least one participant is required");
  if(e.getOfferings().isEmpty())throw bad("At least one priced sector is required");
  validateOfferings(e.getVenue(),e.getOfferings().stream().collect(java.util.stream.Collectors.toMap(EventSectorOffering::getVenueSectorId,EventSectorOffering::getPrice)));
  e.setSnapshot(EventSnapshot.copyOf(e)); e.setStatus(EventStatus.PUBLISHED); return events.saveAndFlush(e);
 }
 public Event cancel(String user,String role,UUID id){
  Event e=authorized(user,role,id);   // ADMIN o ORGANIZER dueño; si no, 403 / 404
  if(e.getStatus()!=EventStatus.PUBLISHED&&e.getStatus()!=EventStatus.CANCELLED)throw conflict("Only PUBLISHED events can be cancelled");
  e.getVenue().getId();
  e.setStatus(EventStatus.CANCELLED);
  inventory.notifyCancelled(e.getId());   // si falla (503), se revierte el cambio de estado
  return e;
}
 public Event suspend(String user,String role,UUID id){identity(user,role);if(!"ADMIN".equals(role))throw forbidden();Event e=events.findById(id).orElseThrow(()->notFound("Event not found"));if(e.getStatus()!=EventStatus.PUBLISHED)throw conflict("Only PUBLISHED events can be suspended");e.getOfferings().size();e.getVenue().getId();e.setStatus(EventStatus.SUSPENDED);return e;}
 public Event resume(String user,String role,UUID id){identity(user,role);if(!"ADMIN".equals(role))throw forbidden();Event e=events.findById(id).orElseThrow(()->notFound("Event not found"));if(e.getStatus()!=EventStatus.SUSPENDED)throw conflict("Only SUSPENDED events can be resumed");e.getOfferings().size();e.getVenue().getId();e.setStatus(EventStatus.PUBLISHED);return e;}
 @Transactional(readOnly=true) public List<Event> list(String user,String role){UUID actor=identity(user,role);List<Event> result;if("ADMIN".equals(role))result=events.findAllByOrderByNameAscIdAsc();else if("ORGANIZER".equals(role))result=events.findAllByOrganizerIdOrderByNameAscIdAsc(actor);else throw forbidden();result.forEach(e->e.getOfferings().size());return result;}
 @Transactional(readOnly=true) public Event get(String user,String role,UUID id){Event e=authorized(user,role,id);e.getOfferings().size();return e;}
 private Event authorized(String user,String role,UUID id){UUID actor=identity(user,role);if(!"ADMIN".equals(role)&&!"ORGANIZER".equals(role))throw forbidden();Event e=events.findById(id).orElseThrow(()->notFound("Event not found"));if("ORGANIZER".equals(role)&&!e.getOrganizerId().equals(actor))throw forbidden();e.getOfferings().size();return e;}
 private Venue venue(UUID id){return venues.findById(id).orElseThrow(()->notFound("Venue not found"));}
 private void validateOfferings(Venue venue,Map<UUID,BigDecimal> prices){if(prices==null)throw bad("Offerings are required");Set<UUID> valid=new HashSet<>();sectors.findAllByVenueId(venue.getId()).forEach(s->valid.add(s.getId()));for(var entry:prices.entrySet()){if(!valid.contains(entry.getKey()))throw bad("Every offered sector must belong to the selected venue");if(entry.getValue()==null||entry.getValue().signum()<=0)throw bad("Offering price must be positive");}}
 private void draft(Event e){if(e.getStatus()!=EventStatus.DRAFT)throw new ApiProblemException(HttpStatus.CONFLICT,"Event is not editable","Only DRAFT events can be edited");}
 private UUID identity(String user,String role){try{if(user==null||user.isBlank()||role==null||role.isBlank())throw new IllegalArgumentException();return UUID.fromString(user);}catch(IllegalArgumentException ex){throw forbidden();}}
 private ApiProblemException forbidden(){return new ApiProblemException(HttpStatus.FORBIDDEN,"Forbidden","A valid identity and permitted role are required");}
 private ApiProblemException notFound(String d){return new ApiProblemException(HttpStatus.NOT_FOUND,"Not found",d);}
 private ApiProblemException bad(String d){return new ApiProblemException(HttpStatus.BAD_REQUEST,"Invalid event",d);}
 private ApiProblemException conflict(String d){return new ApiProblemException(HttpStatus.CONFLICT,"Invalid event state",d);}
}
