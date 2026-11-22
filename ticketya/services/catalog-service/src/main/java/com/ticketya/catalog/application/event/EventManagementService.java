package com.ticketya.catalog.application.event;
import com.ticketya.catalog.api.error.ApiProblemException; import com.ticketya.catalog.domain.event.*; import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.event.EventRepository; import com.ticketya.catalog.infrastructure.persistence.venue.*;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization; import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ticketya.catalog.infrastructure.cache.EventDiscoveryCache;
import com.ticketya.catalog.infrastructure.notification.EventChange; import com.ticketya.catalog.infrastructure.notification.EventNotificationPublisher;
import java.math.BigDecimal; import java.util.*;
import java.time.Clock; import java.time.Duration; import java.time.OffsetDateTime; import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
@Service @Transactional
public class EventManagementService {
 private final EventRepository events; private final VenueRepository venues; private final VenueSectorRepository sectors; private final InventoryCancellationClient inventory; private final EventDiscoveryCache cache; private final EventNotificationPublisher notifications; private final JdbcTemplate jdbc; private final Clock clock;
 public EventManagementService(EventRepository e,VenueRepository v,VenueSectorRepository s,InventoryCancellationClient i,EventDiscoveryCache c,EventNotificationPublisher n,JdbcTemplate jdbc,Clock clock){events=e;venues=v;sectors=s;inventory=i;cache=c;notifications=n;this.jdbc=jdbc;this.clock=clock;}
 public Event create(String user,String role,String name,String description,String type,java.time.OffsetDateTime startsAt,UUID organizer,UUID venueId,List<String> participants,Map<UUID,BigDecimal> offerings,Boolean highDemand){
  UUID actor=identity(user,role); UUID owner;
  if("ADMIN".equals(role)){if(organizer==null)throw bad("ADMIN must provide organizerId");owner=organizer;}else if("ORGANIZER".equals(role)){owner=actor;if(organizer!=null&&!organizer.equals(actor))throw forbidden();}else throw forbidden();
  Venue v=venue(venueId); Event e=new Event(name,description,type,startsAt,owner,v); e.setHighDemand(Boolean.TRUE.equals(highDemand)); if(participants!=null)e.replaceParticipants(participants); if(offerings!=null){validateOfferings(v,offerings);e.replaceOfferings(offerings);} return events.save(e);
 }
 public Event update(String user,String role,UUID id,String name,String description,String type,java.time.OffsetDateTime startsAt,UUID venueId,List<String> participants,Boolean highDemand){
  Event e=authorized(user,role,id); draft(e); Venue v=venue(venueId); e.update(name,description,type,startsAt);if(!e.getVenue().getId().equals(v.getId()))e.clearOfferings();e.setVenue(v);if(participants!=null)e.replaceParticipants(participants);if(highDemand!=null)e.setHighDemand(highDemand);return e;
 }
 public Event offerings(String user,String role,UUID id,Map<UUID,BigDecimal> prices){Event e=authorized(user,role,id);draft(e);validateOfferings(e.getVenue(),prices);e.replaceOfferings(prices);invalidateAfterCommit();return e;}
 public Event publish(String user,String role,UUID id){
  Event e=authorized(user,role,id);
  if(e.getStatus()==EventStatus.PUBLISHED && e.getSnapshot()!=null)return e;
  draft(e);
  if(e.getName()==null||e.getDescription()==null||e.getEventType()==null||e.getVenue()==null)throw bad("Event data is incomplete");
  if(!e.getStartsAt().isAfter(java.time.OffsetDateTime.now()))throw bad("Event start time must be in the future");
  if(e.getParticipants().isEmpty())throw bad("At least one participant is required");
  if(e.getOfferings().isEmpty())throw bad("At least one priced sector is required");
  validateOfferings(e.getVenue(),e.getOfferings().stream().collect(java.util.stream.Collectors.toMap(EventSectorOffering::getVenueSectorId,EventSectorOffering::getPrice)));
  e.setSnapshot(EventSnapshot.copyOf(e)); e.setStatus(EventStatus.PUBLISHED); Event saved=events.saveAndFlush(e); invalidateAfterCommit(); notifyAfterCommit("EVENT_PUBLISHED",saved); return saved;
 }
 public Event suspend(String user,String role,UUID id){identity(user,role);if(!"ADMIN".equals(role))throw forbidden();Event e=events.findById(id).orElseThrow(()->notFound("Event not found"));if(e.getStatus()!=EventStatus.PUBLISHED)throw conflict("Only PUBLISHED events can be suspended");e.getOfferings().size();e.getVenue().getId();e.setStatus(EventStatus.SUSPENDED);invalidateAfterCommit();notifyAfterCommit("EVENT_SUSPENDED",e);return e;}
 public Event resume(String user,String role,UUID id){identity(user,role);if(!"ADMIN".equals(role))throw forbidden();Event e=events.findById(id).orElseThrow(()->notFound("Event not found"));if(e.getStatus()!=EventStatus.SUSPENDED)throw conflict("Only SUSPENDED events can be resumed");e.getOfferings().size();e.getVenue().getId();e.setStatus(EventStatus.PUBLISHED);invalidateAfterCommit();notifyAfterCommit("EVENT_RESUMED",e);return e;}
 public Event cancel(String user,String role,UUID id){
  identity(user,role);
  if(!"ADMIN".equals(role))throw forbidden();
  Event e=events.findById(id).orElseThrow(()->notFound("Event not found"));
  if(e.getStatus()!=EventStatus.PUBLISHED&&e.getStatus()!=EventStatus.SUSPENDED&&e.getStatus()!=EventStatus.CANCELLED)throw conflict("Only PUBLISHED or SUSPENDED events can be cancelled");
  e.getOfferings().size();
  e.getVenue().getId();
  e.setStatus(EventStatus.CANCELLED);
  inventory.notifyCancelled(e.getId());
  invalidateAfterCommit();
  notifyAfterCommit("EVENT_CANCELLED",e);
  return e;
 }
 public ScheduleChange postpone(String user,String role,UUID id,OffsetDateTime startsAt,long expectedRevision,String key){
  UUID actor=identity(user,role);
  if(!"ADMIN".equals(role)&&!"ORGANIZER".equals(role))throw forbidden();
  if(startsAt==null||expectedRevision<0||key==null||key.isBlank()||key.length()>200)throw bad("startsAt, nonnegative expectedScheduleRevision and Idempotency-Key (1-200 characters) are required");
  String normalizedKey=key.trim();
  Event e=events.findForScheduleUpdate(id).orElseThrow(()->notFound("Event not found"));
  if("ORGANIZER".equals(role)&&!e.getOrganizerId().equals(actor))throw forbidden();
  List<ScheduleHistory> prior=jdbc.query("SELECT change_id,actor_id,expected_schedule_revision,previous_starts_at,starts_at,schedule_revision FROM event_schedule_changes WHERE event_id=? AND idempotency_key=?",
    (rs,row)->new ScheduleHistory(rs.getObject("change_id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getLong("expected_schedule_revision"),rs.getObject("previous_starts_at",OffsetDateTime.class),rs.getObject("starts_at",OffsetDateTime.class),rs.getLong("schedule_revision")),id,normalizedKey);
  if(!prior.isEmpty()){
   ScheduleHistory h=prior.get(0);
   if(!h.actorId().equals(actor)||h.expectedRevision()!=expectedRevision||!h.startsAt().toInstant().equals(startsAt.toInstant()))throw conflict("Idempotency-Key was already used with a different request");
   return new ScheduleChange(id,h.changeId(),h.previousStartsAt(),h.startsAt(),h.scheduleRevision());
  }
  if(e.getStatus()!=EventStatus.PUBLISHED)throw conflict("Only PUBLISHED events can be postponed");
  OffsetDateTime previous=e.getStartsAt();
  if(!previous.toInstant().isAfter(clock.instant()))throw conflict("Event has already started");
  if(!startsAt.toInstant().isAfter(previous.toInstant())||!startsAt.toInstant().isAfter(clock.instant().plus(Duration.ofHours(72))))throw conflict("New start time must be later than the current schedule and more than 72 hours from now");
  if(e.getScheduleRevision()!=expectedRevision)throw conflict("Schedule revision is obsolete");
  OffsetDateTime newUtc=startsAt.withOffsetSameInstant(ZoneOffset.UTC);
  OffsetDateTime oldUtc=previous.withOffsetSameInstant(ZoneOffset.UTC);
  e.postpone(newUtc);
  events.saveAndFlush(e);
  UUID changeId=UUID.randomUUID();
  jdbc.update("INSERT INTO event_schedule_changes(change_id,event_id,idempotency_key,actor_id,expected_schedule_revision,previous_starts_at,starts_at,schedule_revision) VALUES (?,?,?,?,?,?,?,?)",
    changeId,id,normalizedKey,actor,expectedRevision,oldUtc,newUtc,e.getScheduleRevision());
  e.getParticipants().size();e.getVenue().getCity();
  EventChange change=EventChange.postponed(id,changeId,e.getName(),oldUtc,newUtc,e.getVenue().getCity(),e.getParticipants().stream().map(EventParticipant::getName).toList());
  invalidateAfterCommit();
  if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){notifications.publish(change);}});
  else notifications.publish(change);
  return new ScheduleChange(id,changeId,oldUtc,newUtc,e.getScheduleRevision());
 }
 public record ScheduleChange(UUID eventId,UUID changeId,OffsetDateTime previousStartsAt,OffsetDateTime startsAt,long scheduleRevision){}
 private record ScheduleHistory(UUID changeId,UUID actorId,long expectedRevision,OffsetDateTime previousStartsAt,OffsetDateTime startsAt,long scheduleRevision){}
 @Transactional(readOnly=true) public List<Event> list(String user,String role){UUID actor=identity(user,role);List<Event> result;if("ADMIN".equals(role))result=events.findAllByOrderByNameAscIdAsc();else if("ORGANIZER".equals(role))result=events.findAllByOrganizerIdOrderByNameAscIdAsc(actor);else throw forbidden();result.forEach(e->e.getOfferings().size());return result;}
 @Transactional(readOnly=true) public Event get(String user,String role,UUID id){Event e=authorized(user,role,id);e.getOfferings().size();return e;}
 private Event authorized(String user,String role,UUID id){UUID actor=identity(user,role);if(!"ADMIN".equals(role)&&!"ORGANIZER".equals(role))throw forbidden();Event e=events.findById(id).orElseThrow(()->notFound("Event not found"));if("ORGANIZER".equals(role)&&!e.getOrganizerId().equals(actor))throw forbidden();e.getOfferings().size();return e;}
 /**
  * PDF-F11: arma el cambio dentro de la transacción (lugar y participantes
  * cargados) y lo envía a Notification Service sólo si la transacción confirma.
  */
 private void notifyAfterCommit(String type,Event e){
  EventChange change=EventChange.of(type,e.getId(),e.getName(),e.getStartsAt(),e.getVenue().getCity(),
    e.getParticipants().stream().map(EventParticipant::getName).toList());
  if(TransactionSynchronizationManager.isSynchronizationActive()){
   TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
    @Override public void afterCommit(){notifications.publish(change);}
   });
  } else notifications.publish(change);
 }
 private void invalidateAfterCommit(){
  if(TransactionSynchronizationManager.isSynchronizationActive()){
   TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
    @Override public void afterCommit(){cache.invalidateAll();}
   });
  } else cache.invalidateAll();
 }
 private Venue venue(UUID id){return venues.findById(id).orElseThrow(()->notFound("Venue not found"));}
 private void validateOfferings(Venue venue,Map<UUID,BigDecimal> prices){if(prices==null)throw bad("Offerings are required");Set<UUID> valid=new HashSet<>();sectors.findAllByVenueId(venue.getId()).forEach(s->valid.add(s.getId()));for(var entry:prices.entrySet()){if(!valid.contains(entry.getKey()))throw bad("Every offered sector must belong to the selected venue");if(entry.getValue()==null||entry.getValue().signum()<=0)throw bad("Offering price must be positive");}}
 private void draft(Event e){if(e.getStatus()!=EventStatus.DRAFT)throw new ApiProblemException(HttpStatus.CONFLICT,"Event is not editable","Only DRAFT events can be edited");}
 private UUID identity(String user,String role){try{if(user==null||user.isBlank()||role==null||role.isBlank())throw new IllegalArgumentException();return UUID.fromString(user);}catch(IllegalArgumentException ex){throw forbidden();}}
 private ApiProblemException forbidden(){return new ApiProblemException(HttpStatus.FORBIDDEN,"Forbidden","A valid identity and permitted role are required");}
 private ApiProblemException notFound(String d){return new ApiProblemException(HttpStatus.NOT_FOUND,"Not found",d);}
 private ApiProblemException bad(String d){return new ApiProblemException(HttpStatus.BAD_REQUEST,"Invalid event",d);}
 private ApiProblemException conflict(String d){return new ApiProblemException(HttpStatus.CONFLICT,"Invalid event state",d);}
}
