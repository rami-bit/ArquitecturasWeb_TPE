package com.ticketya.preferences.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.preferences.api.error.ApiProblemException;
import com.ticketya.preferences.api.error.GlobalExceptionHandler;
import com.ticketya.preferences.api.identity.RequesterArgumentResolver;
import com.ticketya.preferences.api.preferences.CustomerPreferencesController;
import com.ticketya.preferences.api.preferences.CustomerPreferencesResponse;
import com.ticketya.preferences.api.recommendation.EventRecommendationPage;
import com.ticketya.preferences.api.recommendation.RecommendationController;
import com.ticketya.preferences.api.tracing.CorrelationIdFilter;
import com.ticketya.preferences.application.preferences.PreferencesService;
import com.ticketya.preferences.application.recommendation.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de preferencias y recomendaciones, sin levantar Spring ni Docker. */
class CustomerPreferencesApiContractTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("8f14e45f-ceea-467f-a8a4-0f1b2f0f9c11");

    private PreferencesService preferencesService;
    private RecommendationService recommendationService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        preferencesService = mock(PreferencesService.class);
        recommendationService = mock(RecommendationService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new CustomerPreferencesController(preferencesService),
                        new RecommendationController(recommendationService))
                .setCustomArgumentResolvers(new RequesterArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void putRegistraPreferenciasYDevuelveVersion() throws Exception {
        when(preferencesService.replace(eq(CUSTOMER_ID), any())).thenReturn(response("Buenos Aires", 1L));

        mvc.perform(put("/api/customers/me/preferences")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "CLIENT")
                        .header("X-Correlation-Id", "trace-preferences-put")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city":"Buenos Aires","eventTypes":["CONCIERTO"],"favoriteArtists":["Artista A"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Buenos Aires"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(header().string("X-Correlation-Id", "trace-preferences-put"));
    }

    @Test
    void putRechazaIdentidadEnElCuerpoYPreferenciasInvalidas() throws Exception {
        mvc.perform(put("/api/customers/me/preferences")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"00000000-0000-4000-8000-000000000001","city":"Buenos Aires"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.correlationId").exists());

        when(preferencesService.replace(eq(CUSTOMER_ID), any()))
                .thenThrow(ApiProblemException.invalidPreferences("city no puede estar en blanco"));

        mvc.perform(put("/api/customers/me/preferences")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("city no puede estar en blanco"));
    }

    @Test
    void getSinDocumentoDevuelveRepresentacionVacia() throws Exception {
        when(preferencesService.get(CUSTOMER_ID)).thenReturn(response(null, 0L));

        mvc.perform(get("/api/customers/me/preferences")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.eventTypes").isEmpty());
    }

    @Test
    void rolesDistintosDeClientReciben403YSinIdentidadTambien() throws Exception {
        mvc.perform(get("/api/customers/me/preferences")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(get("/api/customers/me/preferences"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recomendacionesUsanLimitePorOmisionYValidanElExplicito() throws Exception {
        when(recommendationService.recommend(any(), anyInt())).thenReturn(page());

        mvc.perform(get("/api/customers/me/recommendations")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "CLIENT")
                        .header("X-Correlation-Id", "trace-recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(header().string("X-Correlation-Id", "trace-recommendations"));

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(recommendationService).recommend(eq(CUSTOMER_ID), limit.capture());
        assertThat(limit.getValue()).isEqualTo(10);

        when(recommendationService.recommend(any(), eq(21)))
                .thenThrow(ApiProblemException.invalidLimit("limit debe ser un número entre 1 y 20"));

        mvc.perform(get("/api/customers/me/recommendations")
                        .param("limit", "21")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit debe ser un número entre 1 y 20"));
    }

    @Test
    void catalogNoDisponibleResponde503ConProblemJson() throws Exception {
        when(recommendationService.recommend(any(), anyInt()))
                .thenThrow(ApiProblemException.serviceUnavailable("Catalog service is unavailable"));

        mvc.perform(get("/api/customers/me/recommendations")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    private static CustomerPreferencesResponse response(String city, long version) {
        return new CustomerPreferencesResponse(city,
                city == null ? List.of() : List.of("CONCIERTO"),
                city == null ? List.of() : List.of("Artista A"),
                city == null ? null : Instant.parse("2026-11-20T12:00:00Z"), version);
    }

    private static EventRecommendationPage page() {
        return new EventRecommendationPage(List.of(), List.of("HISTORY_UNAVAILABLE"),
                Instant.parse("2026-11-20T12:00:00Z"));
    }
}
