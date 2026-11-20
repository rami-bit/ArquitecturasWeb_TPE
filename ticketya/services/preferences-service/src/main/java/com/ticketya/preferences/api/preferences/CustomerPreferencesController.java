package com.ticketya.preferences.api.preferences;

import com.ticketya.preferences.api.identity.CurrentRequester;
import com.ticketya.preferences.api.identity.Requester;
import com.ticketya.preferences.application.preferences.PreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preferencias del cliente autenticado; la identidad se deriva del `sub` del JWT. */
@RestController
@RequestMapping("/api/customers/me/preferences")
@Tag(name = "Customer preferences",
        description = "Preferencias propias del cliente autenticado (solo rol CLIENT).")
public class CustomerPreferencesController {

    private final PreferencesService service;

    public CustomerPreferencesController(PreferencesService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(
            operationId = "replaceCustomerPreferences",
            summary = "Registrar o reemplazar las preferencias del cliente autenticado",
            description = """
                    Upsert atómico de un solo documento: repetir la escritura no crea
                    un segundo documento y devuelve `version` incrementada. El cuerpo
                    no acepta `customerId`, `version` ni `updatedAt`.
                    """)
    @ApiResponse(responseCode = "200", description = "Preferencias registradas.")
    @ApiResponse(responseCode = "400", description = "Preferencias inválidas.")
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido.")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT.")
    public CustomerPreferencesResponse replace(
            @CurrentRequester Requester requester,
            @Valid @RequestBody CustomerPreferencesRequest request) {
        return service.replace(requester.id(), request);
    }

    @GetMapping
    @Operation(
            operationId = "getCustomerPreferences",
            summary = "Consultar las preferencias del cliente autenticado",
            description = "Sin documento devuelve `200` con `version` `0` y listas vacías.")
    @ApiResponse(responseCode = "200", description = "Preferencias del cliente.")
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido.")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT.")
    public ResponseEntity<CustomerPreferencesResponse> get(@CurrentRequester Requester requester) {
        return ResponseEntity.ok(service.get(requester.id()));
    }
}
