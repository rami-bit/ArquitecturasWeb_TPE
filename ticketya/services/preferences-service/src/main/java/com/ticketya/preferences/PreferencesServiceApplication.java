package com.ticketya.preferences;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.ticketya.preferences.config.PreferencesProperties;

/**
 * Preferences Service: preferencias del cliente y recomendaciones de eventos.
 *
 * <p>Persiste un documento por cliente en MongoDB y calcula recomendaciones
 * explicables a partir de las preferencias, del historial de compras
 * (Purchase) y de los candidatos publicados (Catalog), sin acceder a las bases
 * de otros servicios.
 */
@SpringBootApplication
@EnableConfigurationProperties(PreferencesProperties.class)
public class PreferencesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PreferencesServiceApplication.class, args);
    }
}
