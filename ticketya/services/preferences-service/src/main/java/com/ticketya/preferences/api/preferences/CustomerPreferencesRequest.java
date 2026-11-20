package com.ticketya.preferences.api.preferences;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Preferencias que el cliente registra o reemplaza. No acepta `customerId`,
 * `version` ni `updatedAt`: la identidad se deriva del `sub` del JWT y los otros
 * dos campos los gestiona el servidor. Cualquier campo extra responde `400`.
 */
@Schema(name = "CustomerPreferencesRequest")
public record CustomerPreferencesRequest(
        @Schema(nullable = true, maxLength = 120, example = "Buenos Aires")
        @Size(max = 120, message = "city no puede superar los 120 caracteres")
        String city,

        @Schema(description = "Hasta 10 tipos de evento como texto libre.", example = "[\"CONCIERTO\", \"TEATRO\"]")
        @Size(max = 10, message = "eventTypes no puede superar los 10 elementos")
        List<@NotBlank(message = "eventTypes no puede contener elementos vacios")
            @Size(max = 80, message = "cada tipo de evento no puede superar los 80 caracteres")
            String> eventTypes,

        @Schema(description = "Hasta 20 artistas favoritos.", example = "[\"Artista A\", \"Artista B\"]")
        @Size(max = 20, message = "favoriteArtists no puede superar los 20 elementos")
        List<@NotBlank(message = "favoriteArtists no puede contener elementos vacios")
            @Size(max = 200, message = "cada artista no puede superar los 200 caracteres")
            String> favoriteArtists) {

    /** Rechaza `customerId`, `version`, `updatedAt` y cualquier otro campo extra con 400. */
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw com.ticketya.preferences.api.error.ApiProblemException.identityInBody(
                "El cuerpo de preferencias no acepta el campo '" + field + "'");
    }
}
