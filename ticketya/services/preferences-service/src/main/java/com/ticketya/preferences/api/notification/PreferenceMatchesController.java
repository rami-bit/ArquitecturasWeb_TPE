package com.ticketya.preferences.api.notification;

import com.ticketya.preferences.application.notification.PreferenceMatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contrato interno para Notification Service (PDF-F11). No se publica por el
 * Gateway y no expone preferencias, sólo los clientes que coinciden.
 */
@RestController
@RequestMapping("/internal/preferences")
@Tag(name = "Preference matches (internal)", description = "Clientes con un artista favorito en una ciudad")
public class PreferenceMatchesController {
    private final PreferenceMatchService service;

    public PreferenceMatchesController(PreferenceMatchService service) {
        this.service = service;
    }

    @GetMapping("/matches")
    @Operation(operationId = "findPreferenceMatches",
            summary = "Clientes cuya ciudad coincide y que tienen alguno de los artistas como favorito")
    @ApiResponse(responseCode = "200", description = "Identificadores de cliente (posiblemente vacío)")
    @ApiResponse(responseCode = "400", description = "Parámetros fuera de rango")
    public PreferenceMatches matches(
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "artist", required = false) List<String> artists) {
        return new PreferenceMatches(service.fansInCity(city, artists));
    }

    @Schema(name = "PreferenceMatches")
    public record PreferenceMatches(List<UUID> customerIds) {
    }
}
