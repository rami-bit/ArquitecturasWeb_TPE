package com.ticketya.preferences.domain.preferences;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento de preferencias de un cliente. {@code _id} es el UUID del cliente
 * derivado del {@code sub} del JWT; existe un único documento por cliente.
 */
@Document("customer_preferences")
public class CustomerPreferences {

    @Id
    private String id;
    private String city;
    private List<String> eventTypes = new ArrayList<>();
    private List<String> favoriteArtists = new ArrayList<>();
    private Instant updatedAt;
    private long version;

    public CustomerPreferences() {
    }

    public CustomerPreferences(String id, String city, List<String> eventTypes,
                               List<String> favoriteArtists, Instant updatedAt, long version) {
        this.id = id;
        this.city = city;
        this.eventTypes = eventTypes == null ? new ArrayList<>() : new ArrayList<>(eventTypes);
        this.favoriteArtists = favoriteArtists == null
                ? new ArrayList<>() : new ArrayList<>(favoriteArtists);
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static CustomerPreferences empty(String id) {
        return new CustomerPreferences(id, null, List.of(), List.of(), null, 0L);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes == null ? new ArrayList<>() : new ArrayList<>(eventTypes);
    }

    public List<String> getFavoriteArtists() {
        return favoriteArtists;
    }

    public void setFavoriteArtists(List<String> favoriteArtists) {
        this.favoriteArtists = favoriteArtists == null
                ? new ArrayList<>() : new ArrayList<>(favoriteArtists);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    /** Comparación insensible a mayúsculas y espacios extremos, como el scorer. */
    public List<String> normalizedEventTypes() {
        return eventTypes.stream().map(CustomerPreferences::normalize).toList();
    }

    public List<String> normalizedFavoriteArtists() {
        return favoriteArtists.stream().map(CustomerPreferences::normalize).toList();
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
