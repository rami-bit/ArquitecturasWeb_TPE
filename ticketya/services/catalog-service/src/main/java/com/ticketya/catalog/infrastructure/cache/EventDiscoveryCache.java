package com.ticketya.catalog.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Caché en memoria de búsquedas y detalles del catálogo publicado
 * (PDF-NF-CACHE). Sólo guarda lecturas ya resueltas del snapshot publicado:
 * nunca disponibilidad de Inventory, precios de compra ni recomendaciones.
 *
 * <p>Las entradas expiran por TTL y se invalidan de forma explícita cuando
 * cambia el estado o las ofertas de un evento. El tope de entradas evita un
 * crecimiento ilimitado; al alcanzarse se descarta la caché completa.
 */
@Component
public class EventDiscoveryCache {

    private static final Logger log = LoggerFactory.getLogger(EventDiscoveryCache.class);

    private final long ttlMillis;
    private final int maxEntries;
    private final ConcurrentHashMap<String, CacheEntry> values = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();

    public EventDiscoveryCache(
            @Value("${catalog.cache.ttl-seconds:30}") long ttlSeconds,
            @Value("${catalog.cache.max-entries:200}") int maxEntries) {
        this.ttlMillis = Math.max(1, ttlSeconds) * 1000L;
        this.maxEntries = Math.max(1, maxEntries);
    }

    /** Caché desactivada para pruebas unitarias que construyen el servicio directamente. */
    public static EventDiscoveryCache disabled() {
        return new EventDiscoveryCache(30, 1) {
            @Override
            public <T> T get(String key, Supplier<T> loader) {
                return loader.get();
            }
        };
    }

    public <T> T get(String key, Supplier<T> loader) {
        CacheEntry cached = values.get(key);
        if (cached != null && !cached.expired()) {
            hits.incrementAndGet();
            log.debug("cache hit key={}", key);
            return (T) cached.value;
        }
        if (cached != null) {
            values.remove(key);
        }
        misses.incrementAndGet();
        T loaded = loader.get();
        if (values.size() >= maxEntries) {
            values.clear();
            log.debug("cache cleared: reached max entries {}", maxEntries);
        }
        values.put(key, new CacheEntry(loaded, Instant.now().plusMillis(ttlMillis)));
        return loaded;
    }

    /** Invalidación gruesa: cualquier cambio publicado puede afectar búsquedas y detalles. */
    public void invalidateAll() {
        values.clear();
        invalidations.incrementAndGet();
        log.debug("cache invalidated");
    }

    public String searchKey(Object criteria) {
        return "search:" + criteria;
    }

    public String detailKey(UUID eventId) {
        return "detail:" + eventId;
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long invalidations() {
        return invalidations.get();
    }

    public int size() {
        return values.size();
    }

    private record CacheEntry(Object value, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
