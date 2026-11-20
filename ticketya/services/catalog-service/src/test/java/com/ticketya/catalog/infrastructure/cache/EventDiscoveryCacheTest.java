package com.ticketya.catalog.infrastructure.cache;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Comportamiento de la caché en memoria sin Spring. */
class EventDiscoveryCacheTest {

    @Test
    void segundoAccesoEsHitYNoVuelveACargar() {
        EventDiscoveryCache cache = new EventDiscoveryCache(30, 10);
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get("search:1", () -> {
            loads.incrementAndGet();
            return "valor";
        });
        String second = cache.get("search:1", () -> {
            loads.incrementAndGet();
            return "otro";
        });

        assertThat(first).isEqualTo("valor");
        assertThat(second).isEqualTo("valor");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.hits()).isEqualTo(1);
        assertThat(cache.misses()).isEqualTo(1);
    }

    @Test
    void entradaVencidaSeRecarga() throws Exception {
        EventDiscoveryCache cache = new EventDiscoveryCache(1, 10);
        AtomicInteger loads = new AtomicInteger();

        cache.get("detail:1", loads::incrementAndGet);
        Thread.sleep(1_100);
        cache.get("detail:1", loads::incrementAndGet);

        assertThat(loads.get()).isEqualTo(2);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void topeDeEntradasYInvalidacion() {
        EventDiscoveryCache cache = new EventDiscoveryCache(30, 2);

        cache.get("a", () -> 1);
        cache.get("b", () -> 2);
        assertThat(cache.size()).isEqualTo(2);
        cache.get("c", () -> 3);
        assertThat(cache.size()).isEqualTo(1);

        cache.invalidateAll();
        assertThat(cache.size()).isZero();
        assertThat(cache.invalidations()).isEqualTo(1);
    }

    @Test
    void clavesDeBusquedaYDetalleSonDistintas() {
        EventDiscoveryCache cache = new EventDiscoveryCache(30, 10);
        UUID id = UUID.randomUUID();

        assertThat(cache.searchKey("criteria")).startsWith("search:");
        assertThat(cache.detailKey(id)).isEqualTo("detail:" + id);
    }
}
