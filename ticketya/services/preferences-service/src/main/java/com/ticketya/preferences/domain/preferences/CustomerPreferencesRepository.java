package com.ticketya.preferences.domain.preferences;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Acceso al documento de preferencias por {@code _id} (UUID del cliente). El
 * upsert atómico de un solo documento se resuelve en el servicio.
 */
public interface CustomerPreferencesRepository extends MongoRepository<CustomerPreferences, String> {
}
