package com.ticketya.catalog.api.event;

import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.venue.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:event-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc @Transactional
class EventManagementIntegrationTest {
 @Autowired MockMvc mvc; @Autowired VenueRepository venues;
 private final UUID organizer=UUID.fromString("10000000-0000-0000-0000-000000000001");
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
 @Test void rejectsThirdPartyCreationInvalidOfferingsAndLeavesNoDraft() throws Exception {
  Venue v=new Venue("Arena","Av 1","BA",BigDecimal.ZERO,BigDecimal.ZERO);venues.saveAndFlush(v);
  String body="{\"name\":\"Show\",\"description\":\"Live show\",\"eventType\":\"MUSIC\",\"startsAt\":\"2030-01-01T20:00:00-03:00\",\"venueId\":\"%s\",\"organizerId\":\"10000000-0000-0000-0000-000000000002\"}".formatted(v.getId());
  mvc.perform(post("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/management/events").header("X-User-Id",organizer).header("X-User-Role","ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body.replace("10000000-0000-0000-0000-000000000002","10000000-0000-0000-0000-000000000001"))).andExpect(status().isCreated());
  assertThat(org.springframework.test.util.ReflectionTestUtils.getField(v,"id")).isNotNull();
 }
}
