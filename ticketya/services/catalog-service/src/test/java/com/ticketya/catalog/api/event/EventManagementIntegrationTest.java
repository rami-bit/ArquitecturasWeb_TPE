package com.ticketya.catalog.api.event;

import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.venue.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:event-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class EventManagementIntegrationTest {
 @Autowired MockMvc mvc; @Autowired VenueRepository venues; @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 private final UUID organizer=UUID.fromString("10000000-0000-0000-0000-000000000001");
 @org.junit.jupiter.api.BeforeEach void cleanDatabase(){jdbc.update("delete from snapshot_seats");jdbc.update("delete from snapshot_sectors");jdbc.update("delete from snapshot_participants");jdbc.update("delete from event_snapshots");jdbc.update("delete from event_sector_offerings");jdbc.update("delete from event_participants");jdbc.update("delete from events");jdbc.update("delete from venue_seats");jdbc.update("delete from venue_sectors");jdbc.update("delete from venues");}
 @Test void createsAndConfiguresOwnedDraftAndDocumentsManagementContracts() throws Exception {
  Venue v=new Venue("Arena","Av 1","BA",BigDecimal.ZERO,BigDecimal.ZERO);var section=v.addGeneralAdmissionSector("Campo",100);venues.saveAndFlush(v);
  String body="{\"name\":\"Show\",\"description\":\"Live show\",\"eventType\":\"MUSIC\",\"startsAt\":\"2030-01-01T20:00:00-03:00\",\"venueId\":\"%s\",\"participants\":[\"Band\"]}".formatted(v.getId());
  String response=mvc.perform(post("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.organizerId").value(organizer.toString())).andExpect(jsonPath("$.participants[0]").value("Band")).andReturn().getResponse().getContentAsString();
  UUID event=UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("id").asText());
  mvc.perform(put("/api/management/events/"+event+"/offerings").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content("{\"offerings\":[{\"sectorId\":\"%s\",\"price\":25.50}]}".formatted(section.getId())))
    .andExpect(status().isOk()).andExpect(jsonPath("$.offerings[0].price").value(25.5));
  mvc.perform(put("/api/management/events/"+event+"/offerings").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content("{\"offerings\":[{\"sectorId\":\"%s\",\"price\":0}]}".formatted(section.getId())))
    .andExpect(status().isBadRequest());
  mvc.perform(put("/api/management/events/"+event+"/offerings").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content("{\"offerings\":[{\"sectorId\":\"%s\",\"price\":20}]}".formatted(UUID.randomUUID())))
    .andExpect(status().isBadRequest());
  mvc.perform(get("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(event.toString()));
  mvc.perform(get("/api/management/events/"+event).header("X-User-Id",UUID.randomUUID()).header("X-User-Role","ORGANIZER")).andExpect(status().isForbidden());
  String otherBody=body.replace("\"participants\":[\"Band\"]","\"organizerId\":\"10000000-0000-0000-0000-000000000002\"");
  mvc.perform(post("/api/management/events").header("X-User-Id",UUID.randomUUID()).header("X-User-Role","ADMIN").contentType(MediaType.APPLICATION_JSON).content(otherBody)).andExpect(status().isCreated());
  mvc.perform(get("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/management/events").header("X-User-Id",UUID.randomUUID()).header("X-User-Role","ADMIN")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/management/events'].post").exists()).andExpect(jsonPath("$.paths['/api/management/events/{id}/offerings'].put.responses['400']").exists());
 }
 @Test void publishesAtomicallyIsIdempotentAndAdminTransitionsPreserveSnapshot() throws Exception {
  Venue v=new Venue("Arena","Av 1","BA",BigDecimal.ZERO,BigDecimal.ZERO);var sector=v.addNumberedSector("Plateia",java.util.List.of(new com.ticketya.catalog.domain.venue.SeatPosition("A",1)));venues.saveAndFlush(v);
  String body="{\"name\":\"Show\",\"description\":\"Live show\",\"eventType\":\"MUSIC\",\"startsAt\":\"2030-01-01T20:00:00-03:00\",\"venueId\":\"%s\",\"participants\":[\"Band\"]}".formatted(v.getId());
  String response=mvc.perform(post("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  UUID event=UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("id").asText());
  mvc.perform(post("/api/management/events/"+event+"/publish").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isBadRequest());
  assertThat(jdbc.queryForObject("select status from events where id=?",String.class,event)).isEqualTo("DRAFT");
  assertThat(jdbc.queryForObject("select count(*) from event_snapshots where event_id=?",Integer.class,event)).isZero();
  mvc.perform(put("/api/management/events/"+event+"/offerings").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content("{\"offerings\":[{\"sectorId\":\"%s\",\"price\":25.50}]}".formatted(sector.getId()))).andExpect(status().isOk());
  mvc.perform(post("/api/management/events/"+event+"/publish").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
  assertThat(jdbc.queryForObject("select count(*) from event_snapshots where event_id=?",Integer.class,event)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from snapshot_participants",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from snapshot_sectors",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from snapshot_seats",Integer.class)).isEqualTo(1);
  mvc.perform(post("/api/management/events/"+event+"/publish").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("select count(*) from event_snapshots where event_id=?",Integer.class,event)).isEqualTo(1);
  mvc.perform(put("/api/management/events/"+event).header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
  mvc.perform(put("/api/management/events/"+event+"/offerings").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content("{\"offerings\":[{\"sectorId\":\"%s\",\"price\":30}]}".formatted(sector.getId()))).andExpect(status().isConflict());
  mvc.perform(post("/api/management/events/"+event+"/suspend").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isForbidden());
  String snapshotName=jdbc.queryForObject("select venue_name from event_snapshots where event_id=?",String.class,event);
  v.updateDetails("Arena renovada","Av 2","La Plata",BigDecimal.ONE,BigDecimal.ONE);venues.saveAndFlush(v);
  assertThat(jdbc.queryForObject("select venue_name from event_snapshots where event_id=?",String.class,event)).isEqualTo(snapshotName);
  mvc.perform(post("/api/management/events/"+event+"/suspend").header("X-User-Id",UUID.randomUUID()).header("X-User-Role","ADMIN")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
  mvc.perform(post("/api/management/events/"+event+"/suspend").header("X-User-Id",UUID.randomUUID()).header("X-User-Role","ADMIN")).andExpect(status().isConflict());
  mvc.perform(post("/api/management/events/"+event+"/resume").header("X-User-Id",UUID.randomUUID()).header("X-User-Role","ADMIN")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
  mvc.perform(post("/api/management/events/"+event+"/resume").header("X-User-Id",UUID.randomUUID()).header("X-User-Role","ADMIN")).andExpect(status().isConflict());
  assertThat(jdbc.queryForObject("select venue_name from event_snapshots where event_id=?",String.class,event)).isEqualTo(snapshotName);
  String openApi=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/management/events/{id}/publish'].post").exists()).andExpect(jsonPath("$.paths['/api/management/events/{id}/publish'].post.responses['400']").exists()).andExpect(jsonPath("$.paths['/api/management/events/{id}/suspend'].post.responses['403']").exists()).andExpect(jsonPath("$.paths['/api/management/events/{id}/resume'].post.responses['409']").exists()).andReturn().getResponse().getContentAsString();
  assertThat(openApi).contains("SOLD_OUT","CANCELLED","FINISHED").doesNotContain("/sold-out","/cancel","/finish");
 }
 @Test void rejectsThirdPartyCreationInvalidOfferingsAndLeavesNoDraft() throws Exception {
  Venue v=new Venue("Arena","Av 1","BA",BigDecimal.ZERO,BigDecimal.ZERO);venues.saveAndFlush(v);
  String body="{\"name\":\"Show\",\"description\":\"Live show\",\"eventType\":\"MUSIC\",\"startsAt\":\"2030-01-01T20:00:00-03:00\",\"venueId\":\"%s\",\"organizerId\":\"10000000-0000-0000-0000-000000000002\"}".formatted(v.getId());
  mvc.perform(post("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body.replace("10000000-0000-0000-0000-000000000002","10000000-0000-0000-0000-000000000001"))).andExpect(status().isCreated());
  assertThat(org.springframework.test.util.ReflectionTestUtils.getField(v,"id")).isNotNull();
 }
 @Test void rejectedPublicationsLeaveDraftAndNoSnapshot() throws Exception {
  Venue v=new Venue("Arena","Av 1","BA",BigDecimal.ZERO,BigDecimal.ZERO);venues.saveAndFlush(v);
  UUID noParticipants=createDraft(v.getId(),"2030-01-01T20:00:00-03:00", "[]");
  UUID pastDate=createDraft(v.getId(),"2020-01-01T20:00:00-03:00", "[\"Band\"]");
  UUID noOfferings=createDraft(v.getId(),"2030-01-01T20:00:00-03:00", "[\"Band\"]");
  for(UUID id:java.util.List.of(noParticipants,pastDate,noOfferings)){
   mvc.perform(post("/api/management/events/"+id+"/publish").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isBadRequest());
   assertThat(jdbc.queryForObject("select status from events where id=?",String.class,id)).isEqualTo("DRAFT");
   assertThat(jdbc.queryForObject("select count(*) from event_snapshots where event_id=?",Integer.class,id)).isZero();
  }
 }
 @Test void snapshotFailureRollsBackPublicationStateAndRows() throws Exception {
  Venue v=new Venue("Arena","Av 1","BA",BigDecimal.ZERO,BigDecimal.ZERO);var sector=v.addGeneralAdmissionSector("Campo",10);venues.saveAndFlush(v);
  UUID event=createDraft(v.getId(),"2030-01-01T20:00:00-03:00", "[\"Band\"]");
  mvc.perform(put("/api/management/events/"+event+"/offerings").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content("{\"offerings\":[{\"sectorId\":\"%s\",\"price\":25.50}]}".formatted(sector.getId()))).andExpect(status().isOk());
  jdbc.execute("alter table snapshot_sectors add constraint ck_test_snapshot_failure check (price < 0)");
  try {mvc.perform(post("/api/management/events/"+event+"/publish").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER")).andExpect(status().isInternalServerError());}
  finally {jdbc.execute("alter table snapshot_sectors drop constraint ck_test_snapshot_failure");}
  assertThat(jdbc.queryForObject("select status from events where id=?",String.class,event)).isEqualTo("DRAFT");
  assertThat(jdbc.queryForObject("select count(*) from event_snapshots where event_id=?",Integer.class,event)).isZero();
  assertThat(jdbc.queryForObject("select count(*) from snapshot_participants",Integer.class)).isZero();
 }
 private UUID createDraft(UUID venueId,String startsAt,String participants) throws Exception {
  String body="{\"name\":\"Show\",\"description\":\"Live show\",\"eventType\":\"MUSIC\",\"startsAt\":\"%s\",\"venueId\":\"%s\",\"participants\":%s}".formatted(startsAt,venueId,participants);
  String response=mvc.perform(post("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  return UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("id").asText());
 }
}
