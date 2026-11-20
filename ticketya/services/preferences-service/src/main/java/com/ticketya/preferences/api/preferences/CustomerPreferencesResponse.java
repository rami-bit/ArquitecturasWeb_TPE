package com.ticketya.preferences.api.preferences;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Representación de las preferencias de un cliente (versión 0 cuando no tiene documento). */
@Schema(name = "CustomerPreferencesResponse")
public record CustomerPreferencesResponse(
        String city,
        List<String> eventTypes,
        List<String> favoriteArtists,
        Instant updatedAt,
        long version) {
}
